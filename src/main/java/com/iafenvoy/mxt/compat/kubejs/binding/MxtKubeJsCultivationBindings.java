package com.iafenvoy.mxt.compat.kubejs.binding;

import com.iafenvoy.mxt.compat.kubejs.MxtKubeJsApi;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

/**
 * Runtime cultivation operations exposed as {@code MxtCultivation}.
 */
public final class MxtKubeJsCultivationBindings {
    @Info("Adds a finite, non-negative cultivation amount for a resource.")
    public boolean add(LivingEntity entity, String resource, double amount) {
        return MxtKubeJsApi.addCultivation(entity, id(resource), amount);
    }

    @Info("Attempts a server-authoritative realm breakthrough for a resource chain.")
    public Object tryBreakthrough(LivingEntity entity, String resource) {
        return MxtKubeJsApi.tryBreakthrough(entity, id(resource), FormulaContext.of(entity));
    }

    private static Identifier id(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("Invalid MXT identifier: " + raw);
        return id;
    }
}
