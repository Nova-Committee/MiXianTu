package com.iafenvoy.mxt.compat.kubejs.binding;

import com.iafenvoy.mxt.compat.kubejs.MxtKubeJsApi;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/** Runtime ability operations exposed as {@code MxtAbilities}. */
public final class MxtKubeJsAbilityBindings {
    @Info("Uses a granted ability through the server-authoritative ability service.")
    public Object use(Entity entity, String ability) {
        return MxtKubeJsApi.useAbility(entity, id(ability), FormulaContext.of(entity));
    }

    private static Identifier id(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("Invalid MXT identifier: " + raw);
        return id;
    }
}
