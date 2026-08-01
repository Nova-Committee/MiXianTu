package com.iafenvoy.mxt.runtime.curse;

import com.iafenvoy.mxt.attachment.CurseHolderData;
import com.iafenvoy.mxt.attachment.CurseHolderData.State;
import com.iafenvoy.mxt.data.curse.CurseDefinition;
import com.iafenvoy.mxt.event.CurseApplyEvent;
import com.iafenvoy.mxt.event.CurseRemoveEvent;
import com.iafenvoy.mxt.event.CurseRemoveEvent.Post;
import com.iafenvoy.mxt.event.CurseRemoveEvent.Pre;
import com.iafenvoy.mxt.event.CurseRemoveEvent.Reason;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;

/**
 * Attachment bridge for the common curse transaction model. Callers supply a resolved definition for each operation.
 */
public final class CurseService {
    private CurseService() {
    }

    public static ApplyResult apply(CurseHolderData data, Identifier id, CurseDefinition definition, int stacks,
                                    long gameTime, FormulaContext context, String source) {
        return apply(data, id, definition, stacks, gameTime, context, source, NeoForge.EVENT_BUS);
    }

    /**
     * Variant for integrations that own a dedicated event bus.
     */
    public static ApplyResult apply(CurseHolderData data, Identifier id, CurseDefinition definition, int stacks,
                                    long gameTime, FormulaContext context, String source, @NotNull IEventBus eventBus) {
        return apply(data, id, definition, stacks, gameTime, context, source, eventBus, Optional.empty());
    }

    public static ApplyResult apply(CurseHolderData data, Identifier id, CurseDefinition definition, int stacks,
                                    long gameTime, FormulaContext context, String source, IEventBus eventBus,
                                    Optional<Long> durationOverride) {
        CurseApplyEvent.Pre event = new CurseApplyEvent.Pre(data, id, definition, stacks, gameTime, context, source);
        if (eventBus.post(event).isCanceled()) return ApplyResult.cancelledResult();
        CurseLedger ledger = read(data);
        CurseInstance result = ledger.apply(id, definition, event.stacks(), gameTime, context, event.source(), durationOverride);
        write(data, ledger);
        data.markKnown(id);
        eventBus.post(new CurseApplyEvent.Post(data, id, definition, gameTime, context, result));
        return ApplyResult.applied(result);
    }

    /**
     * Full entity-facing transaction: condition, state mutation and post-apply action.
     */
    public static ApplyResult apply(@NotNull Entity target, Identifier id, CurseDefinition definition, int stacks,
                                    long gameTime, FormulaContext context, String source, IEventBus eventBus) {
        if (!definition.applicationCondition().test(target, context)) {
            return ApplyResult.rejected(ApplyFailure.CONDITION);
        }
        ApplyResult result = apply(target.getData(MxtAttachments.CURSE_HOLDER), id,
                definition, stacks, gameTime, context, source, eventBus);
        if (result.applied()) {
            definition.onApply().execute(target, context);
            CurseScheduler.reschedule(target);
        }
        return result;
    }

    public static ApplyResult apply(Entity target, Identifier id, CurseDefinition definition, int stacks,
                                    long gameTime, FormulaContext context, String source) {
        return apply(target, id, definition, stacks, gameTime, context, source, NeoForge.EVENT_BUS);
    }

    public static ApplyResult applyWithDuration(Entity target, Identifier id, CurseDefinition definition, int stacks,
                                                long gameTime, FormulaContext context, String source,
                                                Optional<Long> durationOverride) {
        if (!definition.applicationCondition().test(target, context)) {
            return ApplyResult.rejected(ApplyFailure.CONDITION);
        }
        ApplyResult result = apply(target.getData(MxtAttachments.CURSE_HOLDER), id,
                definition, stacks, gameTime, context, source, NeoForge.EVENT_BUS, durationOverride);
        if (result.applied()) {
            definition.onApply().execute(target, context);
            CurseScheduler.reschedule(target);
        }
        return result;
    }

    public static Optional<CurseInstance> remove(CurseHolderData data, Identifier id) {
        return remove(data, id, Reason.EXPLICIT, -1L, NeoForge.EVENT_BUS);
    }

    public static Optional<CurseInstance> remove(CurseHolderData data, Identifier id, Reason reason, long gameTime) {
        return remove(data, id, reason, gameTime, NeoForge.EVENT_BUS);
    }

    /**
     * Variant for integrations that own a dedicated event bus.
     */
    public static Optional<CurseInstance> remove(CurseHolderData data, Identifier id, Reason reason, long gameTime, @NotNull IEventBus eventBus) {
        State state = data.instances().get(id);
        if (state == null || eventBus.post(new Pre(data, id, state, reason, gameTime)).isCanceled()) {
            return Optional.empty();
        }
        CurseLedger ledger = read(data);
        Optional<CurseInstance> result = ledger.remove(id);
        write(data, ledger);
        result.ifPresent(removed -> eventBus.post(new Post(data, id, state, reason, gameTime)));
        return result;
    }

    /**
     * Removes a curse and refreshes the entity's due schedule after the transaction commits.
     */
    public static Optional<CurseInstance> remove(Entity target, Identifier id, Reason reason, long gameTime) {
        Optional<CurseInstance> result = remove(target.getData(MxtAttachments.CURSE_HOLDER), id, reason, gameTime, NeoForge.EVENT_BUS);
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
    public static int tick(Entity target, long gameTime, Function<Identifier, Optional<CurseDefinition>> definitions,
                           FormulaContext context) {
        CurseHolderData data = target.getData(MxtAttachments.CURSE_HOLDER);
        int executed = 0;
        for (Entry<Identifier, State> entry : data.instances().entrySet()) {
            Optional<CurseDefinition> resolved = definitions.apply(entry.getKey());
            if (resolved.isEmpty()) {
                data.markUnknown(entry.getKey());
                continue;
            }
            data.markKnown(entry.getKey());
            CurseDefinition definition = resolved.get();
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
        Map<Identifier, CurseInstance> instances = new LinkedHashMap<>();
        data.instances().forEach((id, state) -> instances.put(id, new CurseInstance(id, state.stacks(), state.appliedAt(), state.expiresAt(), state.source())));
        return new CurseLedger(instances);
    }

    private static void write(CurseHolderData data, CurseLedger ledger) {
        Map<Identifier, State> instances = new LinkedHashMap<>();
        ledger.snapshot().forEach((id, state) -> instances.put(id, new State(state.stacks(), state.appliedAt(), state.expiresAt(), state.source())));
        data.replace(instances);
    }
}
