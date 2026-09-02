package com.iafenvoy.mxt.runtime.spirit;

import com.iafenvoy.mxt.data.resource.Resource;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * A block entity that can exchange whole units of one resource at a time.
 * Both operations return the part of {@code amount} that could not be moved.
 */
public interface SpiritAccess {
    /**
     * Returns the data-driven capacity for each resource this target can accept.
     */
    Object2IntMap<Holder<Resource>> getCapacity(@Nullable LivingEntity entity);

    /**
     * Attempts to move one resource; the return value is the unaccepted remainder.
     */
    int add(@Nullable LivingEntity entity, Holder<Resource> resource, int amount, boolean simulate);

    /**
     * Attempts to extract one resource; the return value is the unavailable remainder.
     */
    int extract(@Nullable LivingEntity entity, Holder<Resource> resource, int amount, boolean simulate);

    static int requireNonNegative(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Spirit amount must not be negative");
        return amount;
    }
}
