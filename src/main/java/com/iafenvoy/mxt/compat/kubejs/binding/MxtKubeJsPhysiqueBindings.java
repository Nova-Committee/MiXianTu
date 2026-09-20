package com.iafenvoy.mxt.compat.kubejs.binding;

import com.iafenvoy.mxt.compat.kubejs.MxtKubeJsApi;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService.Result;
import com.iafenvoy.mxt.runtime.cultivation.CultivationToggleService;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Runtime physique operations exposed as {@code MxtPhysiques}.
 * <p>
 * A physique is the element-free half of a body's cultivation identity: it grants vanilla attributes and
 * abilities, scales the damage its holder deals and takes, and excludes other physiques by tag. Everything
 * here goes through the same services the data pack actions use, so the holder condition and the exclusive
 * tags are read against the entity as it is right now rather than trusted from the caller.
 */
public final class MxtKubeJsPhysiqueBindings {
    @Info("Every physique the entity holds, sorted, whether or not it is switched on or its definition still exists.")
    public List<String> list(Entity entity) {
        return MxtKubeJsApi.physiques(entity);
    }

    @Info("The held physiques that count right now, sorted: switched-off physiques are left out.")
    public List<String> active(Entity entity) {
        return MxtKubeJsApi.activePhysiques(entity);
    }

    @Info("Whether the entity holds that physique, switched off or not.")
    public boolean has(Entity entity, String physique) {
        return MxtKubeJsApi.hasPhysique(entity, id(physique));
    }

    @Info("Whether that held physique is switched on; false when the entity does not hold it at all.")
    public boolean enabled(Entity entity, String physique) {
        return MxtKubeJsApi.isPhysiqueEnabled(entity, id(physique));
    }

    @Info("Grants a physique through the authoritative service and answers what it did: {changed, failure}, where failure is DISABLED, ALREADY_HELD, CONDITIONS, EXCLUSIVE_CONFLICT or SERVER_ONLY.")
    public Result grant(LivingEntity entity, String physique) {
        return MxtKubeJsApi.grantPhysique(entity, id(physique));
    }

    @Info("Removes a held physique, giving up its attributes, abilities and damage multipliers; false when it was not held.")
    public boolean remove(LivingEntity entity, String physique) {
        return MxtKubeJsApi.removePhysique(entity, id(physique));
    }

    @Info("Switches a held physique on or off without giving it up, re-deriving what the body is granted; {changed, failure} with NOT_HELD when it holds no such physique.")
    public CultivationToggleService.Result setEnabled(LivingEntity entity, String physique, boolean enabled) {
        return MxtKubeJsApi.setPhysiqueEnabled(entity, id(physique), enabled);
    }

    private static Identifier id(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("Invalid MXT identifier: " + raw);
        return id;
    }
}
