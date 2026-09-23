package com.iafenvoy.mxt.runtime.curse;

import com.iafenvoy.mxt.attachment.CurseHolderAttachment;
import com.iafenvoy.mxt.attachment.CurseHolderAttachment.State;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.data.curse.Curse.StackingMode;
import com.iafenvoy.mxt.data.curse.CurseType.Triggered;
import com.iafenvoy.mxt.event.CurseApplyEvent;
import com.iafenvoy.mxt.event.CurseRemoveEvent.Post;
import com.iafenvoy.mxt.event.CurseRemoveEvent.Pre;
import com.iafenvoy.mxt.event.CurseRemoveEvent.Reason;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.Map.Entry;

/**
 * Attachment bridge for the common curse transaction model; callers supply a resolved definition.
 * <p>
 * Three things are centralized here rather than left to each caller: whether an instance is still backed by a
 * loaded definition, whether the same curse is already mid-transaction for that entity, and which runtime
 * subscriptions follow a change.
 */
public final class CurseService {
    // One entity's open curse transactions, so a behaviour that applies or removes the very curse running it
    // cannot recurse forever. The trigger dispatcher guards its own dispatch the same way.
    private static final ThreadLocal<Set<String>> IN_TRANSACTION = ThreadLocal.withInitial(HashSet::new);

    private CurseService() {
    }

    // Where one held instance stands relative to the definitions loaded now.
    public enum DefinitionState {
        /** The definition is loaded and enabled. */
        ACTIVE,
        /** The definition is loaded but carries the {@code #mxt:disabled} tag. */
        DISABLED,
        /** The definition is not in the registry at all any more. */
        UNKNOWN
    }

    // Without a running server there are no datapack registries to judge against, and no curse transaction is
    // server-authoritative anyway, so the instance reads as active.
    public static DefinitionState definitionState(Holder<Curse> curse) {
        if (ServerLifecycleHooks.getCurrentServer() == null) return DefinitionState.ACTIVE;
        Optional<Reference<Curse>> current = MxtDatapackRegistries.rawHolder(MxtResourceKeys.CURSE, HolderHelper.id(curse));
        return current.map(curseReference -> MxtDatapackRegistries.isDisabled(MxtResourceKeys.CURSE, curseReference) ? DefinitionState.DISABLED : DefinitionState.ACTIVE).orElse(DefinitionState.UNKNOWN);
    }

    public static ApplyResult apply(CurseHolderAttachment data, Holder<Curse> curse, int stacks,
                                    long gameTime, FormulaContext context, Identifier source) {
        return apply(data, curse, stacks, gameTime, context, source, NeoForge.EVENT_BUS);
    }

    /**
     * Variant for integrations that own a dedicated event bus.
     */
    public static ApplyResult apply(CurseHolderAttachment data, Holder<Curse> curse, int stacks,
                                    long gameTime, FormulaContext context, Identifier source, @NotNull IEventBus eventBus) {
        return apply(data, curse, stacks, gameTime, context, source, eventBus, Optional.empty());
    }

    public static ApplyResult apply(CurseHolderAttachment data, Holder<Curse> curse, int stacks,
                                    long gameTime, FormulaContext context, Identifier source, IEventBus eventBus,
                                    Optional<Long> durationOverride) {
        Curse definition = curse.value();
        CurseApplyEvent.Pre event = new CurseApplyEvent.Pre(data, curse, stacks, gameTime, context, source);
        if (eventBus.post(event).isCanceled()) return ApplyResult.cancelledResult();
        // A replacement discards an existing instance and reports the displaced one as a removal, so listeners
        // still learn it disappeared; it runs no action of its own, being an outside decision.
        State displaced = definition.stackingMode() == StackingMode.REPLACE ? data.instances().get(curse) : null;
        Set<Identifier> displacedFrom = data.sources().of(curse);
        CurseLedger ledger = read(data);
        Optional<CurseInstance> applied = ledger.apply(curse, event.stacks(), gameTime, context, durationOverride);
        if (applied.isEmpty()) return ApplyResult.rejected(ApplyFailure.INVALID_DURATION);
        CurseInstance result = applied.get();
        write(data, ledger);
        // The shared rule with ability grants: a source leaving cannot remove another source's curse.
        data.sources().grant(curse, event.source());
        data.markKnown(curse);
        if (displaced != null) eventBus.post(new Post(data, curse, displaced, displacedFrom, Reason.REPLACED, gameTime));
        eventBus.post(new CurseApplyEvent.Post(data, curse, gameTime, context, result));
        return ApplyResult.applied(result);
    }

    public static ApplyResult apply(Entity target, Holder<Curse> curse, int stacks,
                                    long gameTime, FormulaContext context, Identifier source) {
        return apply(target, curse, stacks, gameTime, context, source, NeoForge.EVENT_BUS, Optional.empty());
    }

    public static ApplyResult apply(@NotNull Entity target, Holder<Curse> curse, int stacks,
                                    long gameTime, FormulaContext context, Identifier source, IEventBus eventBus) {
        return apply(target, curse, stacks, gameTime, context, source, eventBus, Optional.empty());
    }

    public static ApplyResult applyWithDuration(Entity target, Holder<Curse> curse, int stacks,
                                                long gameTime, FormulaContext context, Identifier source,
                                                Optional<Long> durationOverride) {
        return apply(target, curse, stacks, gameTime, context, source, NeoForge.EVENT_BUS, durationOverride);
    }

    // Full entity-facing transaction: state mutation plus the behaviour a newly created instance owes and the
    // runtime subscriptions that follow it.
    private static ApplyResult apply(Entity target, Holder<Curse> curse, int stacks, long gameTime,
                                     FormulaContext context, Identifier source, IEventBus eventBus, Optional<Long> durationOverride) {
        Curse definition = curse.value();
        DefinitionState state = definitionState(curse);
        if (state != DefinitionState.ACTIVE)
            return ApplyResult.rejected(state == DefinitionState.DISABLED ? ApplyFailure.DISABLED : ApplyFailure.UNKNOWN);
        if (!definition.applicationCondition().test(target, context))
            return ApplyResult.rejected(ApplyFailure.CONDITION);
        String key = transactionKey(target, curse);
        if (!IN_TRANSACTION.get().add(key)) return ApplyResult.rejected(ApplyFailure.REENTRANT);
        try {
            boolean created = !target.getData(MxtAttachments.CURSE_HOLDER).instances().containsKey(curse)
                    || definition.stackingMode() == StackingMode.REPLACE;
            ApplyResult result = apply(target.getData(MxtAttachments.CURSE_HOLDER), curse, stacks, gameTime,
                    context, source, eventBus, durationOverride);
            if (result.applied()) {
                // "On apply" means the instance was created: stacking onto, or refreshing, a curse already held is
                // a later application of the same instance and must not repeat it.
                if (created && !definition.typedType().inert()) definition.onApply().execute(target, context);
                CurseScheduler.reschedule(target);
                CurseTriggerSubscriptions.rebuild(target);
            }
            return result;
        } finally {
            leave(key);
        }
    }

    public static Optional<CurseInstance> remove(CurseHolderAttachment data, Holder<Curse> curse, Reason reason, long gameTime) {
        return remove(data, curse, reason, gameTime, NeoForge.EVENT_BUS);
    }

    /**
     * Variant for integrations that own a dedicated event bus.
     */
    public static Optional<CurseInstance> remove(CurseHolderAttachment data, Holder<Curse> curse, Reason reason, long gameTime, @NotNull IEventBus eventBus) {
        // Captured before the transaction erases them, so listeners hear who was keeping the curse alive.
        return remove(data, curse, data.sources().of(curse), reason, gameTime, eventBus);
    }

    private static Optional<CurseInstance> remove(CurseHolderAttachment data, Holder<Curse> curse, Set<Identifier> sources,
                                                  Reason reason, long gameTime, @NotNull IEventBus eventBus) {
        State state = data.instances().get(curse);
        if (state == null) return Optional.empty();
        // A frozen instance - its definition was disabled or deleted - runs nothing and only ever leaves by an
        // explicit removal, so neither an expiry nor a cure can quietly turn it into a default effect.
        if (definitionState(curse) != DefinitionState.ACTIVE && reason != Reason.EXPLICIT) return Optional.empty();
        if (eventBus.post(new Pre(data, curse, state, sources, reason, gameTime)).isCanceled()) return Optional.empty();
        CurseLedger ledger = read(data);
        Optional<CurseInstance> result = ledger.remove(curse);
        write(data, ledger);
        result.ifPresent(removed -> eventBus.post(new Post(data, curse, state, sources, reason, gameTime)));
        return result;
    }

    // For callers that have nothing richer than the target's own formula context.
    public static Optional<CurseInstance> remove(Entity target, Holder<Curse> curse, Reason reason, long gameTime) {
        return remove(target, curse, reason, gameTime, FormulaContext.of(target));
    }

    // Runs the action that reason belongs to, then refreshes the due schedule and the runtime subscriptions
    // after the transaction commits.
    public static Optional<CurseInstance> remove(Entity target, Holder<Curse> curse, Reason reason, long gameTime,
                                                 FormulaContext context) {
        String key = transactionKey(target, curse);
        if (!IN_TRANSACTION.get().add(key)) return Optional.empty();
        try {
            Optional<CurseInstance> result = remove(target.getData(MxtAttachments.CURSE_HOLDER), curse, reason, gameTime, NeoForge.EVENT_BUS);
            if (result.isPresent()) {
                runRemovalAction(curse.value(), target, context, reason);
                CurseScheduler.reschedule(target);
                CurseTriggerSubscriptions.rebuild(target);
            }
            return result;
        } finally {
            leave(key);
        }
    }

    // The curse only leaves when that was its last source, exactly as an ability granted by two sources survives
    // the loss of one. Returns the instance when this release removed it, empty when the curse stays.
    public static Optional<CurseInstance> release(Entity target, Holder<Curse> curse, Identifier source,
                                                  Reason reason, long gameTime, FormulaContext context) {
        String key = transactionKey(target, curse);
        if (!IN_TRANSACTION.get().add(key)) return Optional.empty();
        try {
            CurseHolderAttachment data = target.getData(MxtAttachments.CURSE_HOLDER);
            if (!data.sources().holds(curse, source)) return Optional.empty();
            Set<Identifier> before = data.sources().of(curse);
            data.sources().revoke(curse, source);
            if (data.sources().holds(curse)) return Optional.empty();
            // Reported with the set as it was before this release, so the caller that let go is visible in the
            // event rather than an empty set that only says "nobody holds it now".
            Optional<CurseInstance> removed = remove(data, curse, before, reason, gameTime, NeoForge.EVENT_BUS);
            if (removed.isPresent()) {
                runRemovalAction(curse.value(), target, context, reason);
                CurseScheduler.reschedule(target);
                CurseTriggerSubscriptions.rebuild(target);
            }
            return removed;
        } finally {
            leave(key);
        }
    }

    // The same call the ability model makes: what that source no longer declares is released. Applying the
    // payloads of newly declared curses is the caller's job, because only it knows the stacks and duration.
    public static boolean reconcileSource(Entity target, Identifier source, Collection<Holder<Curse>> desired,
                                          long gameTime, FormulaContext context) {
        CurseHolderAttachment data = target.getData(MxtAttachments.CURSE_HOLDER);
        Set<Holder<Curse>> previous = data.sources().keysHeldBy(source);
        // Read before the reconcile, so an instance losing its last source here still reports the sources that
        // were keeping it alive rather than the empty set the reconcile leaves behind.
        Map<Holder<Curse>, Set<Identifier>> before = new LinkedHashMap<>();
        previous.forEach(curse -> before.put(curse, data.sources().of(curse)));
        if (!data.sources().reconcile(source, desired)) return false;
        boolean removedAny = false;
        for (Holder<Curse> curse : previous) {
            if (desired.contains(curse) || data.sources().holds(curse)) continue;
            if (remove(data, curse, before.getOrDefault(curse, Set.of()), Reason.EXPLICIT, gameTime, NeoForge.EVENT_BUS).isPresent())
                removedAny = true;
        }
        CurseScheduler.reschedule(target);
        if (removedAny) CurseTriggerSubscriptions.rebuild(target);
        return true;
    }

    // Expiry and cleansing are the only two moments a curse reacts to on its own, so they own an action each;
    // every other reason is an outside decision, and an inert type runs nothing at all.
    private static void runRemovalAction(Curse definition, Entity target, FormulaContext context, Reason reason) {
        if (definition.typedType().inert()) return;
        EntityAction action = switch (reason) {
            case EXPIRED -> definition.onExpire();
            case CLEANSED -> definition.onCleanse();
            default -> null;
        };
        if (action != null) action.execute(target, context);
    }

    public static int tick(Entity target, long gameTime,
                           FormulaContext context) {
        CurseHolderAttachment data = target.getData(MxtAttachments.CURSE_HOLDER);
        int executed = 0;
        boolean changed = false;
        // The attachment's own order, snapshotted before anything runs: it is the order the instances were
        // applied in and it is persisted with them, unlike a sort by definition id.
        for (Entry<Holder<Curse>, State> entry : List.copyOf(data.instances().entrySet())) {
            Holder<Curse> curse = entry.getKey();
            State state = entry.getValue();
            DefinitionState resolved = definitionState(curse);
            if (resolved != DefinitionState.ACTIVE) {
                // Frozen: no periodic effect and no expiry, while the instance itself stays exactly as it is.
                if (resolved == DefinitionState.UNKNOWN) changed |= data.markUnknown(curse);
                continue;
            }
            changed |= data.markKnown(curse);
            Curse definition = curse.value();
            if (definition.typedType().inert()) continue;
            if (state.expiredAt(gameTime) && remove(target, curse, Reason.EXPIRED, gameTime, context).isPresent()) {
                changed = true;
                continue;
            }
            // A triggered curse waits for its triggers, so this loop never runs its effect.
            if (definition.typedType() instanceof Triggered) continue;
            double interval = definition.tickInterval().evaluate(context);
            if (Double.isFinite(interval) && interval > 0.0D
                    && gameTime >= state.appliedAt()
                    && (gameTime - state.appliedAt()) % Math.max(1L, Math.round(interval)) == 0L) {
                definition.onTick().execute(target, context);
                executed++;
            }
        }
        if (changed) {
            CurseScheduler.reschedule(target);
            CurseTriggerSubscriptions.rebuild(target);
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

    public enum ApplyFailure {
        /** The application condition rejected the entity. */
        CONDITION,
        /** A listener cancelled the apply event. */
        CANCELLED,
        /** The caller is a client; curses are server-authoritative. */
        SERVER_ONLY,
        /** The definition carries the {@code #mxt:disabled} tag. */
        DISABLED,
        /** The definition is not in the registry any more. */
        UNKNOWN,
        /** The same curse is already mid-transaction for this entity. */
        REENTRANT,
        /** The resolved duration cannot be honoured, so nothing was written. */
        INVALID_DURATION
    }

    private static String transactionKey(Entity entity, Holder<Curse> curse) {
        return entity.getUUID() + "/" + HolderHelper.id(curse);
    }

    private static void leave(String key) {
        Set<String> active = IN_TRANSACTION.get();
        active.remove(key);
        if (active.isEmpty()) IN_TRANSACTION.remove();
    }

    private static CurseLedger read(CurseHolderAttachment data) {
        Map<Holder<Curse>, CurseInstance> instances = new LinkedHashMap<>();
        data.instances().forEach((curse, state) -> instances.put(curse, new CurseInstance(curse, state.stacks(), state.appliedAt(), state.expiresAt())));
        return new CurseLedger(instances);
    }

    private static void write(CurseHolderAttachment data, CurseLedger ledger) {
        Map<Holder<Curse>, State> instances = new LinkedHashMap<>();
        // The ledger models what a transaction changes; the unknown-definition flag is not part of that, so it is
        // carried over from the state being replaced. Sources live in the attachment's own ledger, untouched.
        ledger.snapshot().forEach((curse, state) -> {
            State previous = data.instances().get(curse);
            instances.put(curse, new State(state.stacks(), state.appliedAt(), state.expiresAt(),
                    previous != null && previous.unknownDefinition()));
        });
        data.replace(instances);
    }
}
