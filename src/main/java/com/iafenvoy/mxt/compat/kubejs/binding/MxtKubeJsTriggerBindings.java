package com.iafenvoy.mxt.compat.kubejs.binding;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.compat.kubejs.MxtKubeJsApi;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsTriggerCallbacks;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsTriggerMatchers;
import com.iafenvoy.mxt.compat.kubejs.callback.TriPredicate;
import com.iafenvoy.mxt.data.trigger.TriggerSignal;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Custom trigger signals exposed as {@code MxtTriggers}.
 *
 * <p>Publishing is what a data pack does through its ability triggers; subscribing lets a server
 * script wait for the same signal without owning a data pack definition. Both sides enter the
 * runtime {@code TriggerDispatcher}, so script subscriptions and data pack triggers observe the
 * exact same dispatch.</p>
 */
public final class MxtKubeJsTriggerBindings {
    @Info("Registers a script trigger matcher. Datapack type: mxt:js")
    public void matcher(String id, TriPredicate<TriggerSignal, JsonObject, FormulaContext> callback) {
        MxtJsTriggerMatchers.register(id, callback);
    }

    @Info("Publishes a custom trigger signal for one entity. Numeric values are also exposed to trigger formulas.")
    public boolean publish(Entity entity, String signal, Map<String, Object> values) {
        return MxtKubeJsApi.publishTrigger(entity, id(signal), values);
    }

    @Info("Subscribes a server script callback to a trigger signal for one entity. Runtime-only; re-create it after a reload.")
    public boolean subscribe(Entity entity, String signal, String key, Consumer<TriggerSignal> callback) {
        return MxtJsTriggerCallbacks.subscribe(entity, id(signal), key, callback, false);
    }

    @Info("Subscribes a callback that removes itself after the first matching signal.")
    public boolean subscribeOnce(Entity entity, String signal, String key, Consumer<TriggerSignal> callback) {
        return MxtJsTriggerCallbacks.subscribe(entity, id(signal), key, callback, true);
    }

    @Info("Removes one script trigger subscription by the key it was registered with.")
    public boolean unsubscribe(Entity entity, String key) {
        return MxtJsTriggerCallbacks.unsubscribe(entity, key);
    }

    @Info("Whether the entity currently has a script subscription under that key.")
    public boolean has(Entity entity, String key) {
        return MxtJsTriggerCallbacks.has(entity, key);
    }

    @Info("The number of script trigger subscriptions the entity currently has.")
    public int subscriptions(Entity entity) {
        return MxtJsTriggerCallbacks.count(entity);
    }

    private static Identifier id(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("Invalid MXT identifier: " + raw);
        return id;
    }
}
