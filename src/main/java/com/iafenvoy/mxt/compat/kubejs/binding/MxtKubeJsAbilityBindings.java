package com.iafenvoy.mxt.compat.kubejs.binding;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.compat.kubejs.MxtKubeJsApi;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsSelectorCallbacks;
import com.iafenvoy.mxt.compat.kubejs.callback.TriFunction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * Runtime ability operations exposed as {@code MxtAbilities}.
 */
public final class MxtKubeJsAbilityBindings {
    @Info("Uses a granted ability through the server-authoritative ability service.")
    public Object use(Entity entity, String ability) {
        return MxtKubeJsApi.useAbility(entity, id(ability), FormulaContext.of(entity));
    }

    @Info("Registers a script ability target selector. Datapack type: mxt:js; the callback returns an array of entities.")
    public void selector(String id, TriFunction<Entity, FormulaContext, JsonObject, List<Entity>> callback) {
        MxtJsSelectorCallbacks.register(id, callback);
    }

    private static Identifier id(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("Invalid MXT identifier: " + raw);
        return id;
    }
}
