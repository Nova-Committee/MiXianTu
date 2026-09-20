package com.iafenvoy.mxt.compat.kubejs.binding;

import com.iafenvoy.mxt.compat.kubejs.MxtKubeJsApi;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * Runtime element operations exposed as {@code MxtElements}.
 * <p>
 * Elements are read off the spirit roots a body carries and off the buildup a body has accumulated, so this is
 * the script-side answer to both halves of the element system: which element a body is, and how much of one is
 * sitting on it. Everything that changes state goes through the same services the data pack path uses.
 */
public final class MxtKubeJsElementBindings {
    @Info("Every element the entity's spirit roots name right now, sorted. Disabled elements and disabled roots contribute nothing.")
    public List<String> list(Entity entity) {
        return MxtKubeJsApi.elements(entity);
    }

    @Info("Whether the entity's spirit roots name that element right now.")
    public boolean has(Entity entity, String element) {
        return MxtKubeJsApi.hasElement(entity, id(element));
    }

    @Info("How much of that element has built up on the entity; 0 when none has, and 0 for a disabled or unknown element.")
    public double amount(Entity entity, String element) {
        return MxtKubeJsApi.elementAmount(entity, id(element));
    }

    @Info("Builds one element up on the entity through the same pipeline a strike uses (negative wears it off), and returns the new total. Reactions whose demand the new total meets fire here.")
    public double attach(Entity entity, String element, double amount) {
        return MxtKubeJsApi.attachElement(entity, id(element), amount);
    }

    private static Identifier id(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("Invalid MXT identifier: " + raw);
        return id;
    }
}
