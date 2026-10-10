package com.iafenvoy.mxt.runtime.resource;

import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.util.matcher.ItemMatcher.Entry;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Matches any stack carrying a resource container: a capability rather than an id, so a content pack's own vessel is
 * covered without touching the file that declared it. The codec is a unit, so {@code type} carries the whole
 * declaration: {@code {"type": "mxt:resource_container"}}.
 */
public record ResourceContainerEntry() implements Entry {
    public static final ResourceContainerEntry INSTANCE = new ResourceContainerEntry();
    public static final MapCodec<ResourceContainerEntry> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean matches(ItemStack stack) {
        return stack.has(MxtDataComponents.RESOURCE_CONTAINER);
    }

    // The component belongs to the stack, so an answer held per item would claim every stack of anything that
    // carries one - including the empty ones, which have nothing to pour.
    @Override
    public boolean itemLevel() {
        return false;
    }

    @Override
    public MapCodec<ResourceContainerEntry> codec() {
        return CODEC;
    }
}
