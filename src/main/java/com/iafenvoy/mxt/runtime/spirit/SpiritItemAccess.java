package com.iafenvoy.mxt.runtime.spirit;

import com.iafenvoy.mxt.data.resource.Resource;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * An item whose individual stack can exchange whole units of spirit power.
 * Both operations return the part of {@code amount} that could not be moved.
 */
public interface SpiritItemAccess {
    /**
     * Returns this stack's current data-driven spirit capacity, including all
     * items in the stack.
     */
    Object2IntMap<Holder<Resource>> getCapacity(@Nullable LivingEntity entity, ItemStack stack);

    /**
     * Attempts to add one resource to this stack.
     */
    int add(@Nullable LivingEntity entity, ItemStack stack, Holder<Resource> resource, int amount, boolean simulate);

    /**
     * Attempts to extract one resource from this stack.
     */
    int extract(@Nullable LivingEntity entity, ItemStack stack, Holder<Resource> resource, int amount, boolean simulate);
}
