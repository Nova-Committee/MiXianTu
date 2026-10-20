package com.iafenvoy.mxt.runtime.trigger;

import com.iafenvoy.mxt.data.trigger.TriggerContext;
import com.iafenvoy.mxt.data.trigger.TriggerSignal;
import com.iafenvoy.mxt.registry.cache.RegistryCaches;
import com.iafenvoy.mxt.runtime.trigger.TriggerSubscription.State;
import com.iafenvoy.mxt.util.profiling.LoggerDispatcher;
import com.iafenvoy.mxt.util.profiling.LoggingLevel;
import com.iafenvoy.mxt.util.profiling.ProfilerScope;
import com.iafenvoy.mxt.util.profiling.ServerProfiling;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.*;

/**
 * Server-side runtime index for trigger subscriptions, never persisted and knowing nothing about the gameplay
 * modules that own them.
 *
 * <p>The signal index is layered by owner because a subscription identity is only unique inside its owner: two
 * entities holding the same definition register the same module and identity and must never share a slot.
 * {@link #DISPATCHING} stops a subscription from re-entering itself through its own signal.
 */
public final class TriggerDispatcher {
    // One line per signal and per subscription, so the trace is only worth reading while developing.
    private static final LoggerDispatcher TRACE = LoggerDispatcher.of(LogUtils.getLogger(), LoggingLevel.DEBUG, LoggingLevel.NONE);
    private static final LoggerDispatcher FAILURE = LoggerDispatcher.of(LogUtils.getLogger(), LoggingLevel.ERROR, LoggingLevel.ERROR);
    private static final Map<UUID, LinkedHashMap<String, TriggerSubscription>> BY_OWNER = new LinkedHashMap<>();
    private static final Map<Identifier, LinkedHashMap<UUID, LinkedHashMap<String, TriggerSubscription>>> BY_SIGNAL =
            new LinkedHashMap<>();
    // Re-entrancy guard: a subscription whose own action publishes the same signal to itself must not recurse.
    private static final ThreadLocal<Set<String>> DISPATCHING =
            ThreadLocal.withInitial(HashSet::new);

    private TriggerDispatcher() {
    }

    public static void register(TriggerSubscription subscription) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.TRIGGER)) {
            String key = ownerKey(subscription);
            TriggerSubscription previous = BY_OWNER.computeIfAbsent(subscription.owner(), ignored -> new LinkedHashMap<>())
                    .put(key, subscription);
            if (previous != null) {
                // A module rebuilds by re-registering its own identities, so a different object is a real override.
                if (previous != subscription)
                    TRACE.log("Replacing trigger subscription {}/{} of {}",
                            subscription.module(), subscription.identity(), subscription.owner());
                removeSignal(previous);
            }
            BY_SIGNAL.computeIfAbsent(subscription.trigger().signalType(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(subscription.owner(), ignored -> new LinkedHashMap<>())
                    .put(key, subscription);
        }
    }

    public static void unregister(UUID owner, String module, String identity) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.TRIGGER)) {
            LinkedHashMap<String, TriggerSubscription> values = BY_OWNER.get(owner);
            if (values == null) return;
            TriggerSubscription removed = values.remove(module + ":" + identity);
            if (removed != null) removeSignal(removed);
            if (values.isEmpty()) BY_OWNER.remove(owner);
        }
    }

    public static void clearModule(UUID owner, String module) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.TRIGGER)) {
            LinkedHashMap<String, TriggerSubscription> values = BY_OWNER.get(owner);
            if (values == null) return;
            String prefix = module + ":";
            List<String> keys = values.keySet().stream().filter(key -> key.startsWith(prefix)).toList();
            keys.forEach(key -> {
                TriggerSubscription removed = values.remove(key);
                if (removed != null) removeSignal(removed);
            });
            if (values.isEmpty()) BY_OWNER.remove(owner);
        }
    }

    public static void clearOwner(UUID owner) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.TRIGGER)) {
            LinkedHashMap<String, TriggerSubscription> values = BY_OWNER.remove(owner);
            if (values != null) values.values().forEach(TriggerDispatcher::removeSignal);
        }
    }

    public static void clearAll() {
        BY_OWNER.clear();
        BY_SIGNAL.clear();
    }

    public static int subscriptionCount() {
        return BY_OWNER.values().stream().mapToInt(Map::size).sum();
    }

    public static int subscriptionCount(UUID owner) {
        LinkedHashMap<String, TriggerSubscription> subscriptions = BY_OWNER.get(owner);
        return subscriptions == null ? 0 : subscriptions.size();
    }

    // Module-scoped: a module that builds its own index asks for its own slice instead of the total, which also
    // contains every other module's subscriptions.
    public static int subscriptionCount(UUID owner, String module) {
        LinkedHashMap<String, TriggerSubscription> subscriptions = BY_OWNER.get(owner);
        if (subscriptions == null) return 0;
        String prefix = module + ":";
        int count = 0;
        for (String key : subscriptions.keySet()) if (key.startsWith(prefix)) count++;
        return count;
    }

    public static boolean hasSubscription(UUID owner, String module, String identity) {
        LinkedHashMap<String, TriggerSubscription> subscriptions = BY_OWNER.get(owner);
        return subscriptions != null && subscriptions.containsKey(module + ":" + identity);
    }

    // A snapshot, for diagnostics.
    public static List<TriggerSubscription> subscriptions(UUID owner) {
        LinkedHashMap<String, TriggerSubscription> subscriptions = BY_OWNER.get(owner);
        return subscriptions == null ? List.of() : List.copyOf(subscriptions.values());
    }

    public static Map<String, Integer> subscriptionCountsByModule() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        BY_OWNER.values().forEach(subscriptions -> subscriptions.values().forEach(subscription ->
                counts.merge(subscription.module(), 1, Integer::sum)));
        return Collections.unmodifiableMap(counts);
    }

    // Callers that would otherwise build a context nobody reads ask this first, which is what keeps a per-tick
    // signal affordable.
    public static boolean hasListener(Identifier signal) {
        LinkedHashMap<UUID, LinkedHashMap<String, TriggerSubscription>> byOwner = BY_SIGNAL.get(signal);
        if (byOwner != null && !byOwner.isEmpty()) return true;
        return !RegistryCaches.TRIGGER_RULES_BY_SIGNAL.get(signal).isEmpty();
    }

    // Every signal at least one owner listens to, in a stable order, for command completion.
    public static List<Identifier> signals() {
        return BY_SIGNAL.keySet().stream().sorted(Comparator.comparing(Identifier::toString)).toList();
    }

    public static void publish(TriggerSignal signal) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.TRIGGER)) {
            if (signal.context().level() != null && signal.context().level().isClientSide()) return;
            Entity actor = signal.context().actor();
            // Automation and command plumbing drive fake players. They have no session to act with, so they do
            // not publish: vanilla refuses them at the same point, when awarding progress.
            if (actor instanceof FakePlayer) {
                TRACE.log("Ignoring trigger signal {} published by a fake player", signal.type());
                return;
            }
            // Datapack rules are reactions of their own and run before any subscription is considered.
            TriggerRuleService.dispatch(signal);
            if (actor == null) return;
            publishTo(actor.getUUID(), signal);
        }
    }

    // For signals whose context has no actor; publish(...) derives the owner from the actor instead.
    public static void publishTo(UUID owner, TriggerSignal signal) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.TRIGGER)) {
            if (signal.context().level() != null && signal.context().level().isClientSide()) return;
            LinkedHashMap<UUID, LinkedHashMap<String, TriggerSubscription>> byOwner = BY_SIGNAL.get(signal.type());
            LinkedHashMap<String, TriggerSubscription> values = byOwner == null ? null : byOwner.get(owner);
            if (values == null || values.isEmpty()) {
                TRACE.log("Trigger signal {} for {} has no subscriptions", signal.type(), owner);
                return;
            }
            TRACE.log("Publishing trigger signal {} for {} to {} subscriptions",
                    signal.type(), owner, values.size());
            // A one-shot subscription removes itself while it runs, so the loop walks a snapshot of this owner.
            List<TriggerSubscription> snapshot = List.copyOf(values.values());
            try {
                for (TriggerSubscription subscription : snapshot) {
                    if (!subscription.accepts(signal)) continue;
                    // The owner is part of the key: an identity is only unique per owner, so two entities holding the same
                    // definition produce the same value on purpose, and one owner's dispatch must not make another owner's
                    // identical subscription look like a re-entry of its own.
                    String dispatchKey = owner + ":" + ownerKey(subscription) + ":" + signal.type();
                    if (!DISPATCHING.get().add(dispatchKey)) continue;
                    // The key is released on every exit from this subscription - a thrown failure, a consumed one-shot
                    // that unregisters itself, or a plain return - so a broken subscriber cannot wedge its own signal.
                    try {
                        TRACE.log("Trigger signal {} matched {}/{} for {}",
                                signal.type(), subscription.module(), subscription.identity(), owner);
                        subscription.invoke(signal);
                        TRACE.log("Trigger subscription {}/{} completed for {}",
                                subscription.module(), subscription.identity(), signal.type());
                    } catch (RuntimeException exception) {
                        FAILURE.log("Trigger subscription '" + subscription.identity() + "' failed for " + signal.type(), exception);
                    } finally {
                        if (subscription.state() == State.CONSUMED) {
                            TRACE.log("One-shot trigger subscription {}/{} was consumed",
                                    subscription.module(), subscription.identity());
                            unregister(subscription.owner(), subscription.module(), subscription.identity());
                        }
                        DISPATCHING.get().remove(dispatchKey);
                    }
                }
            } finally {
                if (DISPATCHING.get().isEmpty()) DISPATCHING.remove();
            }
        }
    }

    public static void publish(Identifier type, TriggerContext context, long gameTime) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.TRIGGER)) {
            publish(new TriggerSignal(type, context, null, gameTime));
        }
    }

    public static void publishTo(UUID owner, Identifier type, TriggerContext context, long gameTime) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.TRIGGER)) {
            publishTo(owner, new TriggerSignal(type, context, null, gameTime));
        }
    }

    // Not unique across owners: the same definition held by two entities produces the same value on purpose.
    private static String ownerKey(TriggerSubscription subscription) {
        return subscription.module() + ":" + subscription.identity();
    }

    private static void removeSignal(TriggerSubscription subscription) {
        LinkedHashMap<UUID, LinkedHashMap<String, TriggerSubscription>> byOwner = BY_SIGNAL.get(subscription.trigger().signalType());
        if (byOwner == null) return;
        LinkedHashMap<String, TriggerSubscription> values = byOwner.get(subscription.owner());
        if (values == null) return;
        values.remove(ownerKey(subscription));
        if (values.isEmpty()) byOwner.remove(subscription.owner());
        if (byOwner.isEmpty()) BY_SIGNAL.remove(subscription.trigger().signalType());
    }
}
