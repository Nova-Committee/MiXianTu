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
 * Runtime spirit root operations exposed as {@code MxtSpiritRoots}.
 * <p>
 * A spirit root is the element half of a body's cultivation identity: holding one binds an element, changes
 * how fast that element's aura is cultivated and scales the abilities adapted to it. Everything here goes
 * through the same services the data pack actions use, so a script cannot grant a root the conflict rules
 * would have refused.
 * <p>
 * Reads work on either side - the identity attachment is synchronised, so a client script (an item tooltip
 * asking whether the holder is a fire cultivator) reads the same copy the information panel does - while
 * everything that changes state is server-only and says so through the returned result.
 */
public final class MxtKubeJsSpiritRootBindings {
    @Info("Every spirit root the entity holds, sorted, whether or not it is switched on or its definition still exists.")
    public List<String> list(Entity entity) {
        return MxtKubeJsApi.spiritRoots(entity);
    }

    @Info("The held spirit roots that count right now, sorted: switched-off roots and roots bound to a disabled element are left out.")
    public List<String> active(Entity entity) {
        return MxtKubeJsApi.activeSpiritRoots(entity);
    }

    @Info("Whether the entity holds that spirit root, switched off or not.")
    public boolean has(Entity entity, String root) {
        return MxtKubeJsApi.hasSpiritRoot(entity, id(root));
    }

    @Info("Whether that held spirit root is switched on; false when the entity does not hold it at all.")
    public boolean enabled(Entity entity, String root) {
        return MxtKubeJsApi.isSpiritRootEnabled(entity, id(root));
    }

    @Info("Grants a spirit root through the authoritative service and answers what it did: {changed, failure}, where failure is DISABLED, ALREADY_HELD, ELEMENT_CONFLICT or SERVER_ONLY.")
    public Result grant(LivingEntity entity, String root) {
        return MxtKubeJsApi.grantSpiritRoot(entity, id(root));
    }

    @Info("Removes a held spirit root, giving up its element and everything it granted; false when it was not held.")
    public boolean remove(LivingEntity entity, String root) {
        return MxtKubeJsApi.removeSpiritRoot(entity, id(root));
    }

    @Info("Switches a held spirit root on or off without giving it up, re-deriving what the body is granted; {changed, failure} with NOT_HELD when it holds no such root.")
    public CultivationToggleService.Result setEnabled(LivingEntity entity, String root, boolean enabled) {
        return MxtKubeJsApi.setSpiritRootEnabled(entity, id(root), enabled);
    }

    private static Identifier id(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("Invalid MXT identifier: " + raw);
        return id;
    }
}
