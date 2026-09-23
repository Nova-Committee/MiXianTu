package com.iafenvoy.mxt.data.cost;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.api.AuraAccess;
import com.iafenvoy.mxt.attachment.AuraChunkAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment.Audit;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.builtin.JsCost;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostFailure;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Evaluation;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Result;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The one place a costs array is paid from. Planning is read-only and decides whether the whole array can be
 * paid; committing writes every channel, and anything already written is put back when a later channel refuses,
 * so a payment is all-or-nothing.
 * <p>
 * Write order is chosen so that the only channel that cannot be staged - a script - runs after every channel that
 * can still be rolled back, and the inventory is written last, where nothing can fail any more.
 */
public final class CostTransaction {
    private CostTransaction() {
    }

    /**
     * Plans and pays in one call. Nothing is written unless every entry can be paid.
     */
    public static PayResult pay(List<Cost> costs, CostContext context) {
        Planning planning = plan(costs, context);
        return planning.ok() ? commit(planning, context) : PayResult.denied(planning);
    }

    /**
     * Evaluates and checks every entry without writing anything. The returned plan carries mutable amount maps:
     * callers that must adjust a price before paying (a script event, the shared-pool allocation) change them and
     * then call {@link #commit(Planning, CostContext)}.
     */
    public static Planning plan(List<Cost> costs, CostContext context) {
        return plan(costs, context, null, null);
    }

    /**
     * Planning against a detached view instead of the live attachment. A caller that previews several payments in
     * a row (a composite ability) accumulates them in one resource draft and one item draft, so the second
     * payment is checked against what the first one would leave behind.
     */
    public static Planning plan(List<Cost> costs, CostContext context, @Nullable ResourceHolderAttachment resourceView,
                                @Nullable ItemCostDraft itemView) {
        Planning planning = evaluate(costs, context);
        if (!planning.ok()) return planning;
        CostFailure unavailable = checkAvailability(planning, context, resourceView, itemView);
        return unavailable == null ? planning : Planning.denied(unavailable, -1);
    }

    /**
     * Plans without asking whether the stores can pay. For a caller that scales the amounts first - the shared
     * aura pool is split between everyone cultivating in the same chunk, so what a payer owes is decided before
     * anything is spent - and that relies on {@link #commit} to validate what it actually spends.
     */
    public static Planning planDeferred(List<Cost> costs, CostContext context) {
        return evaluate(costs, context);
    }

    private static Planning evaluate(List<Cost> costs, CostContext context) {
        LinkedHashMap<Identifier, Double> resources = new LinkedHashMap<>();
        LinkedHashMap<Holder<Aura>, Double> auras = new LinkedHashMap<>();
        List<Charge.Items> items = new ArrayList<>();
        List<JsCost> scripts = new ArrayList<>();
        for (int index = 0; index < costs.size(); index++) {
            Either<Charge, CostFailure> result = costs.get(index).charge(context);
            if (result.left().isEmpty()) return Planning.denied(result.right().orElse(null), index);
            switch (result.left().get()) {
                case Charge.Resources(Map<Identifier, Double> amounts) ->
                    // Two entries that reach the same resource by different routes add up: that is the one answer
                    // that does not depend on the order they were written in.
                        amounts.forEach((id, amount) -> resources.merge(id, amount, Double::sum));
                case Charge.Auras(Map<Holder<Aura>, Double> amounts) ->
                        amounts.forEach((aura, amount) -> auras.merge(aura, amount, Double::sum));
                case Charge.Items itemCharge -> items.add(itemCharge);
                case Charge.Script(JsCost cost) -> scripts.add(cost);
                default -> {
                }
            }
        }
        return new Planning(resources, auras, items, scripts, null, -1);
    }

    /**
     * Pays a plan produced by {@link #plan}. Rolls everything back when a channel refuses mid-way.
     */
    public static PayResult commit(Planning planning, CostContext context) {
        return commit(planning, context, null);
    }

    /**
     * Pays a plan into a detached resource view instead of the live attachment. Only abilities pass a view, and
     * only for the resource channel: a preview draft is written by the caller, never by this method.
     */
    public static PayResult commit(Planning planning, CostContext context,
                                   @Nullable ResourceHolderAttachment resourceView) {
        if (!planning.ok()) return PayResult.denied(planning);
        List<Runnable> rollbacks = new ArrayList<>();

        // A bank is the one store another system can empty between planning and paying.
        if (!planning.auras().isEmpty() && context.auraTarget() == CostContext.AuraTarget.BANK) {
            AuraAccess bank = context.bank();
            if (bank == null) return PayResult.denied(CostFailure.NO_CHANNEL, -1);
            Map<Holder<Aura>, Integer> taken = new LinkedHashMap<>();
            // Registered before the first extraction, because a later aura in the same array can still refuse.
            rollbacks.add(() -> taken.forEach((aura, units) -> bank.insert(context.payer(), aura, units, false)));
            for (Map.Entry<Holder<Aura>, Double> entry : planning.auras().entrySet()) {
                int units = units(entry.getValue());
                if (units <= 0) {
                    rollback(rollbacks);
                    return PayResult.denied(CostFailure.INVALID_AMOUNT, -1);
                }
                if (bank.extract(context.payer(), entry.getKey(), units, true) != 0) {
                    rollback(rollbacks);
                    return PayResult.denied(CostFailure.INSUFFICIENT_AURA, -1);
                }
                bank.extract(context.payer(), entry.getKey(), units, false);
                taken.put(entry.getKey(), units);
            }
        }

        // The shared pool is all-or-nothing on its own.
        if (!planning.auras().isEmpty() && context.auraTarget() == CostContext.AuraTarget.POOL) {
            Level level = context.level();
            BlockPos pos = context.pos();
            if (level == null || pos == null) return PayResult.denied(CostFailure.NO_CHANNEL, -1);
            if (!AuraService.consume(level, pos, planning.auras())) {
                rollback(rollbacks);
                return PayResult.denied(CostFailure.INSUFFICIENT_AURA, -1);
            }
            Map<Holder<Aura>, Double> refund = new LinkedHashMap<>(planning.auras());
            rollbacks.add(() -> AuraService.change(level, pos, refund));
        }

        // Resources: the draft validates every entry before the target account is touched.
        if (!planning.resources().isEmpty()) {
            ResourceHolderAttachment target = resourceView != null ? resourceView : context.resourceTarget();
            if (target == null) {
                rollback(rollbacks);
                return PayResult.denied(CostFailure.NO_PAYER, -1);
            }
            Map<Holder<Resource>, ResourceSnapshot> before = snapshot(target, planning.resources().keySet());
            ResourceHolderAttachment draft = target.copy();
            Result result = ResourceTransactions.tryConsume(context.payer(), draft, new Evaluation(planning.resources()));
            if (!result.committed()) {
                rollback(rollbacks);
                return PayResult.denied(CostFailure.INSUFFICIENT_RESOURCE, -1, result.failedResource());
            }
            before.keySet().forEach(resource -> {
                Audit audit = draft.audit(resource);
                target.set(resource, draft.get(resource), audit.minSnapshot(), audit.maxSnapshot(),
                        audit.lastChangedTick(), audit.source());
            });
            rollbacks.add(() -> before.values().forEach(value -> target.set(value.resource(), value.value(),
                    value.min(), value.max(), value.changedAt(), value.source())));
        }

        // Items are staged, not written: reserving them cannot fail after this point, and writing them waits
        // until every channel that can still refuse has been paid.
        ItemCostDraft itemDraft = null;
        if (!planning.items().isEmpty()) {
            Player player = context.player();
            if (player == null) {
                rollback(rollbacks);
                return PayResult.denied(CostFailure.NO_CHANNEL, -1);
            }
            itemDraft = new ItemCostDraft(player);
            for (Charge.Items item : planning.items()) {
                if (itemDraft.reserve(item)) continue;
                rollback(rollbacks);
                return PayResult.denied(CostFailure.MISSING_ITEM, -1);
            }
        }

        // Scripts own their state and cannot be staged, so they run last among the channels that can refuse.
        for (JsCost script : planning.scripts()) {
            if (script.consume(context)) continue;
            rollback(rollbacks);
            return PayResult.denied(CostFailure.SCRIPT_REJECTED, -1);
        }

        if (itemDraft != null) itemDraft.commit();
        return PayResult.paid(planning);
    }

    private static CostFailure checkAvailability(Planning planning, CostContext context,
                                                 @Nullable ResourceHolderAttachment resourceView,
                                                 @Nullable ItemCostDraft itemView) {
        if (!planning.resources().isEmpty()) {
            ResourceHolderAttachment view = resourceView != null ? resourceView : context.resourceTarget();
            if (view == null) return CostFailure.NO_PAYER;
            Result preview = ResourceTransactions.tryConsume(context.payer(), view.copy(), new Evaluation(planning.resources()));
            if (!preview.committed()) return CostFailure.INSUFFICIENT_RESOURCE;
        }
        if (!planning.auras().isEmpty()) {
            CostFailure failure = checkAuras(planning.auras(), context);
            if (failure != null) return failure;
        }
        if (!planning.items().isEmpty()) {
            Player player = context.player();
            if (player == null) return CostFailure.NO_CHANNEL;
            // A caller previewing several payments keeps one draft and passes it back in, so the second payment
            // is checked against what the first one already took.
            ItemCostDraft draft = itemView != null ? itemView : new ItemCostDraft(player);
            for (Charge.Items item : planning.items()) if (!draft.reserve(item)) return CostFailure.MISSING_ITEM;
        }
        return null;
    }

    private static CostFailure checkAuras(Map<Holder<Aura>, Double> auras, CostContext context) {
        if (context.auraTarget() == CostContext.AuraTarget.BANK) {
            AuraAccess bank = context.bank();
            if (bank == null) return CostFailure.NO_CHANNEL;
            for (Map.Entry<Holder<Aura>, Double> entry : auras.entrySet()) {
                int units = units(entry.getValue());
                if (units <= 0) return CostFailure.INVALID_AMOUNT;
                if (bank.extract(context.payer(), entry.getKey(), units, true) != 0)
                    return CostFailure.INSUFFICIENT_AURA;
            }
            return null;
        }
        if (context.auraTarget() == CostContext.AuraTarget.POOL) {
            Level level = context.level();
            BlockPos pos = context.pos();
            if (level == null || pos == null) return CostFailure.NO_CHANNEL;
            AuraChunkAttachment chunk = level.getChunkAt(pos).getData(MxtAttachments.AURA_CHUNK);
            return chunk.canConsume(auras) ? null : CostFailure.INSUFFICIENT_AURA;
        }
        // A payer target turns aura into the resource it is measured in, so there is no aura left to ask about.
        return null;
    }

    /**
     * Whole units, the only granularity a bank or pool deals in for a display-sized price.
     */
    private static int units(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0D) return 0;
        return amount >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.ceil(amount);
    }

    private static Map<Holder<Resource>, ResourceSnapshot> snapshot(ResourceHolderAttachment holder,
                                                                    Collection<Identifier> ids) {
        Map<Holder<Resource>, ResourceSnapshot> snapshots = new LinkedHashMap<>();
        for (Identifier id : ids) {
            MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, id).ifPresent(resource -> {
                Audit audit = holder.audit(resource);
                snapshots.put(resource, new ResourceSnapshot(resource, holder.get(resource), audit.minSnapshot(),
                        audit.maxSnapshot(), audit.lastChangedTick(), audit.source()));
            });
        }
        return snapshots;
    }

    // A rollback that cannot run is louder than a wrong balance: the payment is abandoned and the reason logged.
    private static void rollback(List<Runnable> rollbacks) {
        for (int index = rollbacks.size() - 1; index >= 0; index--) {
            try {
                rollbacks.get(index).run();
            } catch (RuntimeException exception) {
                MiXianTu.LOGGER.error("Could not roll back part of a cost payment; a balance may be wrong", exception);
            }
        }
    }

    private record ResourceSnapshot(Holder<Resource> resource, double value, double min, double max, long changedAt,
                                    String source) {
    }

    /**
     * What a costs array would take. The amount maps are mutable on purpose: a caller may adjust a price before
     * committing it, and every channel then pays exactly what the plan says.
     */
    public record Planning(Map<Identifier, Double> resources, Map<Holder<Aura>, Double> auras,
                           List<Charge.Items> items, List<JsCost> scripts, CostFailure failure, int failedIndex) {
        public Planning {
            resources = new LinkedHashMap<>(resources);
            auras = new LinkedHashMap<>(auras);
            items = List.copyOf(items);
            scripts = List.copyOf(scripts);
        }

        public boolean ok() {
            return this.failure == null;
        }

        static Planning denied(CostFailure failure, int index) {
            return new Planning(Map.of(), Map.of(), List.of(), List.of(), failure, index);
        }
    }

    public record PayResult(boolean paid, CostFailure failure, int failedIndex, Identifier failedResource,
                            Map<Identifier, Double> resources, Map<Holder<Aura>, Double> auras) {
        public PayResult {
            resources = new LinkedHashMap<>(resources);
            auras = new LinkedHashMap<>(auras);
        }

        static PayResult paid(Planning planning) {
            return new PayResult(true, null, -1, null, planning.resources(), planning.auras());
        }

        static PayResult denied(Planning planning) {
            return new PayResult(false, planning.failure(), planning.failedIndex(), null, Map.of(), Map.of());
        }

        static PayResult denied(CostFailure failure, int index) {
            return new PayResult(false, failure, index, null, Map.of(), Map.of());
        }

        static PayResult denied(CostFailure failure, int index, Identifier failedResource) {
            return new PayResult(false, failure, index, failedResource, Map.of(), Map.of());
        }
    }
}
