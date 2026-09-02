package com.iafenvoy.mxt.compat.kubejs.binding;

import com.iafenvoy.mxt.compat.kubejs.MxtKubeJsApi;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.world.entity.Entity;

/** Soul recovery operation exposed as {@code MxtSouls}. */
public final class MxtKubeJsSoulBindings {
    @Info("Reclaims the entity's transferable soul using the authoritative recovery service.")
    public boolean reclaim(Entity entity) {
        return MxtKubeJsApi.reclaimSoul(entity);
    }
}
