package com.iafenvoy.mxt.compat.kubejs.binding;

import com.iafenvoy.mxt.compat.kubejs.MxtKubeJsApi;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * Runtime curse operations exposed as {@code MxtCurses}.
 */
public final class MxtKubeJsCurseBindings {
    @Info("Applies a curse through the standard transactional service.")
    public Object apply(Entity entity, String curse, int stacks, String source) {
        if (stacks < 1) throw new IllegalArgumentException("Curse stacks must be positive");
        if (source == null || source.isBlank()) throw new IllegalArgumentException("Curse source must not be blank");
        return MxtKubeJsApi.applyCurse(entity, id(curse), stacks, source, FormulaContext.of(entity));
    }

    @Info("Removes a curse using the explicit-removal path.")
    public boolean remove(Entity entity, String curse) {
        return MxtKubeJsApi.removeCurse(entity, id(curse));
    }

    private static Identifier id(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("Invalid MXT identifier: " + raw);
        return id;
    }
}
