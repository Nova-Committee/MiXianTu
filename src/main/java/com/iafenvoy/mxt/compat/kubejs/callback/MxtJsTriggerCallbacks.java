package com.iafenvoy.mxt.compat.kubejs.callback;

import com.iafenvoy.mxt.compat.kubejs.MxtJsWarnings;
import com.iafenvoy.mxt.data.trigger.Trigger.Builtin;
import com.iafenvoy.mxt.data.trigger.TriggerSignal;
import com.iafenvoy.mxt.runtime.trigger.TriggerDispatcher;
import com.iafenvoy.mxt.runtime.trigger.TriggerSubscription;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Runtime trigger subscriptions created by server scripts. Nothing here is saved: an entity leaving the
 * world, a server stop, a data pack reload and a script reload all drop them. {@link TriggerDispatcher} is
 * the single source of truth for what exists; this class only remembers which owners registered.
 */
public final class MxtJsTriggerCallbacks {
    /**
     * Module name used with {@link TriggerDispatcher}, keeping script subscriptions separate from the
     * ability and cultivation modules.
     */
    public static final String MODULE = "kubejs";

    private static final Set<UUID> OWNERS = ConcurrentHashMap.newKeySet();

    private MxtJsTriggerCallbacks() {
    }

    public static boolean subscribe(Entity entity, Identifier signal, String key, Consumer<TriggerSignal> callback, boolean oneShot) {
        if (entity.level().isClientSide()) {
            MxtJsWarnings.warnOnce("trigger.client." + signal + '.' + key,
                    "MxtTriggers.subscribe was called for a client entity; script trigger subscriptions are server-only");
            return false;
        }
        if (key == null || key.isBlank()) {
            MxtJsWarnings.warnOnce("trigger.key." + signal,
                    "MxtTriggers.subscribe needs a non-empty key so the subscription can be replaced or removed");
            return false;
        }
        TriggerDispatcher.register(new TriggerSubscription(entity.getUUID(), MODULE, key,
                new Builtin(signal), ignored -> true, callback, oneShot));
        OWNERS.add(entity.getUUID());
        return true;
    }

    public static boolean unsubscribe(Entity entity, String key) {
        if (key == null || !TriggerDispatcher.hasSubscription(entity.getUUID(), MODULE, key)) return false;
        TriggerDispatcher.unregister(entity.getUUID(), MODULE, key);
        return true;
    }

    public static boolean has(Entity entity, String key) {
        return key != null && TriggerDispatcher.hasSubscription(entity.getUUID(), MODULE, key);
    }

    public static int count(Entity entity) {
        return TriggerDispatcher.subscriptionCount(entity.getUUID(), MODULE);
    }

    /**
     * Called when server scripts are re-evaluated, because the callback objects they reference are
     * replaced by the new evaluation.
     */
    public static void clear() {
        OWNERS.forEach(owner -> TriggerDispatcher.clearModule(owner, MODULE));
        OWNERS.clear();
    }
}
