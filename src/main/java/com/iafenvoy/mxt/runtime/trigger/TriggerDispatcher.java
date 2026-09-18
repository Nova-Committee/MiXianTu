package com.iafenvoy.mxt.runtime.trigger;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.trigger.TriggerContext;
import com.iafenvoy.mxt.data.trigger.TriggerSignal;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.runtime.trigger.TriggerSubscription.State;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.*;

/**
 * Server-side runtime index for trigger subscriptions. It has no knowledge of
 * the owning gameplay module and never persists subscription objects.
 *
 * <p>The signal index is layered by owner, because a subscription identity is only unique inside its
 * owner: two entities holding the same definition register the same module and identity, and they must
 * never share a slot. Publishing is then one lookup per layer, and an owner that listens to nothing is
 * rejected before anything is copied.</p>
 */
public final class TriggerDispatcher {
    private static final Map<UUID, LinkedHashMap<String, TriggerSubscription>> BY_OWNER = new LinkedHashMap<>();
    private static final Map<Identifier, LinkedHashMap<UUID, LinkedHashMap<String, TriggerSubscription>>> BY_SIGNAL =
            new LinkedHashMap<>();
    private static final ThreadLocal<Set<String>> DISPATCHING =
            ThreadLocal.withInitial(HashSet::new);

    private TriggerDispatcher() {
    }

    public static void register(TriggerSubscription subscription) {
        String key = ownerKey(subscription);
        TriggerSubscription previous = BY_OWNER.computeIfAbsent(subscription.owner(), ignored -> new LinkedHashMap<>())
                .put(key, subscription);
        if (previous != null) {
            // A module rebuilds by re-registering its own identities, so a different object is a real override.
            if (previous != subscription)
                MiXianTu.LOGGER.debug("Replacing trigger subscription {}/{} of {}",
                        subscription.module(), subscription.identity(), subscription.owner());
            removeSignal(previous);
        }
        BY_SIGNAL.computeIfAbsent(subscription.trigger().signalType(), ignored -> new LinkedHashMap<>())
                .computeIfAbsent(subscription.owner(), ignored -> new LinkedHashMap<>())
                .put(key, subscription);
    }

    public static void unregister(UUID owner, String module, String identity) {
        LinkedHashMap<String, TriggerSubscription> values = BY_OWNER.get(owner);
        if (values == null) return;
        TriggerSubscription removed = values.remove(module + ":" + identity);
        if (removed != null) removeSignal(removed);
        if (values.isEmpty()) BY_OWNER.remove(owner);
    }

    public static void clearModule(UUID owner, String module) {
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

    public static void clearOwner(UUID owner) {
        LinkedHashMap<String, TriggerSubscription> values = BY_OWNER.remove(owner);
        if (values != null) values.values().forEach(TriggerDispatcher::removeSignal);
    }

    public static void clearAll() {
        BY_OWNER.clear();
        BY_SIGNAL.clear();
    }

    /**
     * Returns the runtime-only subscription count, for lifecycle diagnostics. Subscriptions are never
     * persisted.
     */
    public static int subscriptionCount() {
        return BY_OWNER.values().stream().mapToInt(Map::size).sum();
    }

    /**
     * Returns the current runtime-only subscription count for one owner.
     */
    public static int subscriptionCount(UUID owner) {
        LinkedHashMap<String, TriggerSubscription> subscriptions = BY_OWNER.get(owner);
        return subscriptions == null ? 0 : subscriptions.size();
    }

    /**
     * Returns the current runtime-only subscription count of one module for one
     * owner. Modules that build their own index ask for their own slice instead
     * of the total, which also contains the subscriptions of every other module.
     */
    public static int subscriptionCount(UUID owner, String module) {
        LinkedHashMap<String, TriggerSubscription> subscriptions = BY_OWNER.get(owner);
        if (subscriptions == null) return 0;
        String prefix = module + ":";
        int count = 0;
        for (String key : subscriptions.keySet()) if (key.startsWith(prefix)) count++;
        return count;
    }

    /**
     * Whether one module currently owns the subscription with that identity.
     */
    public static boolean hasSubscription(UUID owner, String module, String identity) {
        LinkedHashMap<String, TriggerSubscription> subscriptions = BY_OWNER.get(owner);
        return subscriptions != null && subscriptions.containsKey(module + ":" + identity);
    }

    /**
     * Returns a snapshot of every runtime-only subscription of one owner, for diagnostics. Subscriptions are
     * never persisted.
     */
    public static List<TriggerSubscription> subscriptions(UUID owner) {
        LinkedHashMap<String, TriggerSubscription> subscriptions = BY_OWNER.get(owner);
        return subscriptions == null ? List.of() : List.copyOf(subscriptions.values());
    }

    /**
     * Returns a stable module-to-subscription count snapshot for diagnostics.
     */
    public static Map<String, Integer> subscriptionCountsByModule() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        BY_OWNER.values().forEach(subscriptions -> subscriptions.values().forEach(subscription ->
                counts.merge(subscription.module(), 1, Integer::sum)));
        return Collections.unmodifiableMap(counts);
    }

    /**
     * Whether anything at all can react to that signal: a subscription of any owner, or a datapack rule.
     * Callers that would otherwise build a context nobody reads ask this first, which is what keeps the
     * per-tick signal affordable.
     */
    public static boolean hasListener(Identifier signal) {
        LinkedHashMap<UUID, LinkedHashMap<String, TriggerSubscription>> byOwner = BY_SIGNAL.get(signal);
        if (byOwner != null && !byOwner.isEmpty()) return true;
        return ServerCache.get().map(cache -> !cache.triggerRules(signal).isEmpty()).orElse(false);
    }

    /**
     * Every signal at least one owner listens to, in a stable order, for command completion.
     */
    public static List<Identifier> signals() {
        return BY_SIGNAL.keySet().stream().sorted(Comparator.comparing(Identifier::toString)).toList();
    }

    public static void publish(TriggerSignal signal) {
        if (signal.context().level() != null && signal.context().level().isClientSide()) return;
        Entity actor = signal.context().actor();
        // Automation and command plumbing drive fake players. They have no session to act with, so they do
        // not publish: vanilla refuses them at the same point, when awarding progress.
        if (actor instanceof FakePlayer) {
            MiXianTu.LOGGER.debug("Ignoring trigger signal {} published by a fake player", signal.type());
            return;
        }
        // Datapack rules are reactions of their own and run before any subscription is considered.
        TriggerRuleService.dispatch(signal);
        if (actor == null) return;
        publishTo(actor.getUUID(), signal);
    }

    /**
     * Publishes a signal to one owner explicitly, for signals whose context has no actor; the normal
     * publish method derives the owner from {@link TriggerContext#actor()}.
     */
    public static void publishTo(UUID owner, TriggerSignal signal) {
        if (signal.context().level() != null && signal.context().level().isClientSide()) return;
        LinkedHashMap<UUID, LinkedHashMap<String, TriggerSubscription>> byOwner = BY_SIGNAL.get(signal.type());
        LinkedHashMap<String, TriggerSubscription> values = byOwner == null ? null : byOwner.get(owner);
        if (values == null || values.isEmpty()) {
            MiXianTu.LOGGER.debug("Trigger signal {} for {} has no subscriptions", signal.type(), owner);
            return;
        }
        MiXianTu.LOGGER.debug("Publishing trigger signal {} for {} to {} subscriptions",
                signal.type(), owner, values.size());
        // A one-shot subscription removes itself while it runs, so the loop walks a snapshot of this owner.
        List<TriggerSubscription> snapshot = List.copyOf(values.values());
        try {
            for (TriggerSubscription subscription : snapshot) {
                if (!subscription.accepts(signal)) continue;
                String dispatchKey = subscription.module() + ":" + subscription.identity() + ":" + signal.type();
                if (!DISPATCHING.get().add(dispatchKey)) continue;
                try {
                    MiXianTu.LOGGER.debug("Trigger signal {} matched {}/{} for {}",
                            signal.type(), subscription.module(), subscription.identity(), owner);
                    subscription.invoke(signal);
                    MiXianTu.LOGGER.debug("Trigger subscription {}/{} completed for {}",
                            subscription.module(), subscription.identity(), signal.type());
                } catch (RuntimeException exception) {
                    MiXianTu.LOGGER.error("Trigger subscription {} failed for {}", subscription.identity(), signal.type(), exception);
                }
                if (subscription.state() == State.CONSUMED) {
                    MiXianTu.LOGGER.debug("One-shot trigger subscription {}/{} was consumed",
                            subscription.module(), subscription.identity());
                    unregister(subscription.owner(), subscription.module(), subscription.identity());
                }
                DISPATCHING.get().remove(dispatchKey);
            }
        } finally {
            if (DISPATCHING.get().isEmpty()) DISPATCHING.remove();
        }
    }

    public static void publish(Identifier type, TriggerContext context, long gameTime) {
        publish(new TriggerSignal(type, context, null, gameTime));
    }

    public static void publishTo(UUID owner, Identifier type, TriggerContext context, long gameTime) {
        publishTo(owner, new TriggerSignal(type, context, null, gameTime));
    }

    /**
     * The identity of a subscription inside its owner. It is not unique across owners: the same definition
     * held by two entities produces the same value on purpose.
     */
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
