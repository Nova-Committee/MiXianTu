package com.iafenvoy.mxt.runtime.curse;

import com.iafenvoy.mxt.attachment.CurseHolderData;
import com.iafenvoy.mxt.attachment.CurseHolderData.State;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.event.CurseApplyEvent;
import com.iafenvoy.mxt.event.CurseRemoveEvent.Post;
import com.iafenvoy.mxt.event.CurseRemoveEvent.Pre;
import com.iafenvoy.mxt.event.CurseRemoveEvent.Reason;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

/**
 * Attachment bridge for the common curse transaction model. Callers supply a resolved definition for each operation.
 */
public final class CurseService {
    private CurseService() {
    }

    public static ApplyResult apply(CurseHolderData data, Holder<Curse> curse, int stacks,
                                    long gameTime, FormulaContext context, String source) {
        return apply(data, curse, stacks, gameTime, context, source, NeoForge.EVENT_BUS);
    }

    /**
     * Variant for integrations that own a dedicated event bus.
     */
    public static ApplyResult apply(CurseHolderData data, Holder<Curse> curse, int stacks,
                                    long gameTime, FormulaContext context, String source, @NotNull IEventBus eventBus) {
        return apply(data, curse, stacks, gameTime, context, source, eventBus, Optional.empty());
    }

    public static ApplyResult apply(CurseHolderData data, Holder<Curse> curse, int stacks,
                                    long gameTime, FormulaContext context, String source, IEventBus eventBus,
                                    Optional<Long> durationOverride) {
        Curse definition = curse.value();
        Identifier id = HolderHelper.id(curse);
        CurseApplyEvent.Pre event = new CurseApplyEvent.Pre(data, id, definition, stacks, gameTime, context, source);
        if (eventBus.post(event).isCanceled()) return ApplyResult.cancelledResult();
        CurseLedger ledger = read(data);
        CurseInstance result = ledger.apply(curse, event.stacks(), gameTime, context, event.source(), durationOverride);
        write(data, ledger);
        data.markKnown(curse);
        eventBus.post(new CurseApplyEvent.Post(data, id, definition, gameTime, context, result));
        return ApplyResult.applied(result);
    }

    /**
     * Full entity-facing transaction: condition, state mutation and post-apply action.
     */
    public static ApplyResult apply(@NotNull Entity target, Holder<Curse> curse, int stacks,
                                    long gameTime, FormulaContext context, String source, IEventBus eventBus) {
        Curse definition = curse.value();
        if (!definition.applicationCondition().test(target, context)) {
            return ApplyResult.rejected(ApplyFailure.CONDITION);
        }
        ApplyResult result = apply(target.getData(MxtAttachments.CURSE_HOLDER), curse,
                stacks, gameTime, context, source, eventBus);
        if (result.applied()) {
            definition.onApply().execute(target, context);
            CurseScheduler.reschedule(target);
        }
        return result;
    }

    public static ApplyResult apply(Entity target, Holder<Curse> curse, int stacks,
                                    long gameTime, FormulaContext context, String source) {
        return apply(target, curse, stacks, gameTime, context, source, NeoForge.EVENT_BUS);
    }

    public static ApplyResult applyWithDuration(Entity target, Holder<Curse> curse, int stacks,
                                                long gameTime, FormulaContext context, String source,
                                                Optional<Long> durationOverride) {
        if (!curse.value().applicationCondition().test(target, context)) {
            return ApplyResult.rejected(ApplyFailure.CONDITION);
        }
        ApplyResult result = apply(target.getData(MxtAttachments.CURSE_HOLDER), curse,
                stacks, gameTime, context, source, NeoForge.EVENT_BUS, durationOverride);
        if (result.applied()) {
            curse.value().onApply().execute(target, context);
            CurseScheduler.reschedule(target);
        }
        return result;
    }

    public static Optional<CurseInstance> remove(CurseHolderData data, Holder<Curse> curse, Reason reason, long gameTime) {
        return remove(data, curse, reason, gameTime, NeoForge.EVENT_BUS);
    }

    /**
     * Variant for integrations that own a dedicated event bus.
     */
    public static Optional<CurseInstance> remove(CurseHolderData data, Holder<Curse> curse, Reason reason, long gameTime, @NotNull IEventBus eventBus) {
        Identifier id = HolderHelper.id(curse);
        State state = data.instances().get(curse);
        if (state == null || eventBus.post(new Pre(data, id, state, reason, gameTime)).isCanceled()) {
            return Optional.empty();
        }
        CurseLedger ledger = read(data);
        Optional<CurseInstance> result = ledger.remove(curse);
        write(data, ledger);
        result.ifPresent(removed -> eventBus.post(new Post(data, id, state, reason, gameTime)));
        return result;
    }

    /**
     * Removes a curse and refreshes the entity's due schedule after the transaction commits.
     */
    public static Optional<CurseInstance> remove(Entity target, Holder<Curse> curse, Reason reason, long gameTime) {
        Optional<CurseInstance> result = remove(target.getData(MxtAttachments.CURSE_HOLDER), curse, reason, gameTime, NeoForge.EVENT_BUS);
        if (result.isPresent()) CurseScheduler.reschedule(target);
        return result;
    }

    public static List<CurseInstance> removeExpired(CurseHolderData data, long gameTime) {
        return data.instances().entrySet().stream()
                .filter(entry -> entry.getValue().expiredAt(gameTime))
                .map(entry -> remove(data, entry.getKey(), Reason.EXPIRED, gameTime))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Runs due periodic effects and expires instances without scanning unrelated entities.
     */
    public static int tick(Entity target, long gameTime,
                           FormulaContext context) {
        CurseHolderData data = target.getData(MxtAttachments.CURSE_HOLDER);
        int executed = 0;
        for (Entry<Holder<Curse>, State> entry : data.instances().entrySet()) {
            data.markKnown(entry.getKey());
            Curse definition = entry.getKey().value();
            if (entry.getValue().expiredAt(gameTime)) {
                if (remove(data, entry.getKey(), Reason.EXPIRED, gameTime).isPresent()) {
                    definition.onRemove().execute(target, context);
                }
            }
            double interval = definition.tickInterval().evaluate(context);
            if (Double.isFinite(interval) && interval > 0.0D
                    && gameTime >= entry.getValue().appliedAt()
                    && (gameTime - entry.getValue().appliedAt()) % Math.max(1L, Math.round(interval)) == 0L) {
                definition.onTick().execute(target, context);
                executed++;
            }
        }
        return executed;
    }

    public record ApplyResult(CurseInstance instance, boolean cancelled, ApplyFailure failure) {
        private static ApplyResult applied(CurseInstance instance) {
            return new ApplyResult(instance, false, null);
        }

        private static ApplyResult cancelledResult() {
            return new ApplyResult(null, true, ApplyFailure.CANCELLED);
        }

        private static ApplyResult rejected(ApplyFailure failure) {
            return new ApplyResult(null, false, failure);
        }

        public boolean applied() {
            return this.instance != null;
        }
    }

    public enum ApplyFailure {CONDITION, CANCELLED, SERVER_ONLY}

    private static CurseLedger read(CurseHolderData data) {
        Map<Holder<Curse>, CurseInstance> instances = new LinkedHashMap<>();
        data.instances().forEach((curse, state) -> instances.put(curse, new CurseInstance(curse, state.stacks(), state.appliedAt(), state.expiresAt(), state.source())));
        return new CurseLedger(instances);
    }

    private static void write(CurseHolderData data, CurseLedger ledger) {
        Map<Holder<Curse>, State> instances = new LinkedHashMap<>();
        ledger.snapshot().forEach((curse, state) -> instances.put(curse, new State(state.stacks(), state.appliedAt(), state.expiresAt(), state.source())));
        data.replace(instances);
    }
}
