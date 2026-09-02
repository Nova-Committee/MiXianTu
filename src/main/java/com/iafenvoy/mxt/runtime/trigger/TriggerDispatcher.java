package com.iafenvoy.mxt.runtime.trigger;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.trigger.TriggerContext;
import com.iafenvoy.mxt.data.trigger.TriggerSignal;
import com.iafenvoy.mxt.runtime.trigger.TriggerSubscription.State;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.*;

/**
 * Server-side runtime index for trigger subscriptions. It has no knowledge of
 * the owning gameplay module and never persists subscription objects.
 */
public final class TriggerDispatcher {
    private static final Map<UUID, LinkedHashMap<String, TriggerSubscription>> BY_OWNER = new LinkedHashMap<>();
    private static final Map<Identifier, LinkedHashMap<String, TriggerSubscription>> BY_SIGNAL = new LinkedHashMap<>();
    private static final ThreadLocal<Set<String>> DISPATCHING =
            ThreadLocal.withInitial(HashSet::new);

    private TriggerDispatcher() {
    }

    public static void register(TriggerSubscription subscription) {
        String key = key(subscription);
        TriggerSubscription previous = BY_OWNER.computeIfAbsent(subscription.owner(), ignored -> new LinkedHashMap<>())
                .put(key, subscription);
        if (previous != null) {
            LinkedHashMap<String, TriggerSubscription> oldSignals = BY_SIGNAL.get(previous.trigger().signalType());
            if (oldSignals != null) {
                oldSignals.remove(key);
                if (oldSignals.isEmpty()) BY_SIGNAL.remove(previous.trigger().signalType());
            }
        }
        BY_SIGNAL.computeIfAbsent(subscription.trigger().signalType(), ignored -> new LinkedHashMap<>())
                .put(key, subscription);
    }

    public static void unregister(UUID owner, String module, String identity) {
        LinkedHashMap<String, TriggerSubscription> values = BY_OWNER.get(owner);
        if (values == null) return;
        String key = module + ":" + identity;
        TriggerSubscription removed = values.remove(key);
        if (removed != null) removeSignal(removed, key);
        if (values.isEmpty()) BY_OWNER.remove(owner);
    }

    public static void clearModule(UUID owner, String module) {
        LinkedHashMap<String, TriggerSubscription> values = BY_OWNER.get(owner);
        if (values == null) return;
        List<String> keys = values.keySet().stream().filter(key -> key.startsWith(module + ":")).toList();
        keys.forEach(key -> {
            TriggerSubscription removed = values.remove(key);
            if (removed != null) removeSignal(removed, key);
        });
        if (values.isEmpty()) BY_OWNER.remove(owner);
    }

    public static void clearOwner(UUID owner) {
        LinkedHashMap<String, TriggerSubscription> values = BY_OWNER.remove(owner);
        if (values != null) values.forEach((key, subscription) -> removeSignal(subscription, key));
    }

    public static void clearAll() {
        BY_OWNER.clear();
        BY_SIGNAL.clear();
    }

    /**
     * Returns the current runtime-only subscription count. This is primarily
     * intended for lifecycle diagnostics; subscriptions themselves are never
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
     * Returns a stable module-to-subscription count snapshot for diagnostics.
     */
    public static Map<String, Integer> subscriptionCountsByModule() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        BY_OWNER.values().forEach(subscriptions -> subscriptions.values().forEach(subscription ->
                counts.merge(subscription.module(), 1, Integer::sum)));
        return Collections.unmodifiableMap(counts);
    }

    public static void publish(TriggerSignal signal) {
        if (signal.context().level() != null && signal.context().level().isClientSide()) return;
        Entity actor = signal.context().actor();
        if (actor == null) return;
        publishTo(actor.getUUID(), signal);
    }

    /**
     * Publishes a signal to one owner explicitly. This is useful for signals
     * whose context has no actor (for example a world/system event), while the
     * normal publish method derives the owner from {@link TriggerContext#actor()}.
     */
    public static void publishTo(UUID owner, TriggerSignal signal) {
        if (signal.context().level() != null && signal.context().level().isClientSide()) return;
        LinkedHashMap<String, TriggerSubscription> values = new LinkedHashMap<>();
        BY_SIGNAL.getOrDefault(signal.type(), new LinkedHashMap<>()).forEach(values::putIfAbsent);
        int ownerCount = (int) values.values().stream().filter(subscription -> subscription.owner().equals(owner)).count();
        if (ownerCount == 0) {
            MiXianTu.LOGGER.debug("Trigger signal {} for {} has no subscriptions", signal.type(), owner);
            return;
        }
        MiXianTu.LOGGER.debug("Publishing trigger signal {} for {} to {} subscriptions",
                signal.type(), owner, ownerCount);
        try {
            List<TriggerSubscription> snapshot = new ArrayList<>(values.values());
            for (TriggerSubscription subscription : snapshot) {
                if (!subscription.owner().equals(owner)) continue;
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

    private static String key(TriggerSubscription subscription) {
        return subscription.module() + ":" + subscription.identity();
    }

    private static void removeSignal(TriggerSubscription subscription, String key) {
        Identifier signal = subscription.trigger().signalType();
        LinkedHashMap<String, TriggerSubscription> values = BY_SIGNAL.get(signal);
        if (values == null) return;
        values.remove(key);
        if (values.isEmpty()) BY_SIGNAL.remove(signal);
    }
}
