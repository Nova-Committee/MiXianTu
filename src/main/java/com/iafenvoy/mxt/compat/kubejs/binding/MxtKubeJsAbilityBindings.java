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
 * <p>
 * An ability grant is kept alive by its sources, the same way a curse is: granting adds one, revoking drops one,
 * and the ability only disappears once no source holds it.
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

    @Info("Grants an ability under the given source; true when that source was not holding it yet.")
    public boolean grant(Entity entity, String ability, String source) {
        return MxtKubeJsApi.grantAbility(entity, id(ability), sourceId(source));
    }

    @Info("Lets go of one source's claim; the ability only leaves when no other source holds it. Only a claim the entity actually holds reports true.")
    public boolean revoke(Entity entity, String ability, String source) {
        return MxtKubeJsApi.revokeAbility(entity, id(ability), sourceId(source));
    }

    @Info("Whether the entity currently holds that ability.")
    public boolean has(Entity entity, String ability) {
        return MxtKubeJsApi.hasAbility(entity, id(ability));
    }

    @Info("Every ability the entity holds right now, sorted.")
    public List<String> list(Entity entity) {
        return MxtKubeJsApi.abilities(entity);
    }

    @Info("Which sources are keeping that ability granted right now, sorted.")
    public List<String> sources(Entity entity, String ability) {
        return MxtKubeJsApi.abilitySources(entity, id(ability));
    }

    private static Identifier id(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("Invalid MXT identifier: " + raw);
        return id;
    }

    /**
     * A source is an identifier now that abilities and curses share the ledger, so a script has to name itself the
     * way every built-in source does.
     */
    private static Identifier sourceId(String raw) {
        Identifier id = raw == null ? null : Identifier.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("Invalid ability source identifier: " + raw
                + " (use a namespaced id such as example:quest_reward)");
        return id;
    }
}
