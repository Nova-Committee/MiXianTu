package com.iafenvoy.mxt.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public final class EntityReferences {
    private EntityReferences() {
    }

    public static @Nullable Entity inLevel(@Nullable EntityReference<Entity> reference, Level level) {
        if (reference == null) return null;
        Entity entity = reference.getEntity(level::getEntity, Entity.class);
        // A cached entity can bypass the lookup after changing levels.
        return entity != null && entity.level() == level ? entity : null;
    }
}
