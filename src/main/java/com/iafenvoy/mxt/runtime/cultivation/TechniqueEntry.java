package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.util.matcher.ItemMatcher.Entry;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Matches a stack carrying the {@code mxt:technique} component, which is what makes a manual: the declaration
 * lives on the stack rather than on the item, so this is the one matcher that reads component data instead of the
 * item's identity. The codec is a unit, so {@code type} carries the whole declaration:
 * {@code {"type": "mxt:technique"}}.
 */
public record TechniqueEntry() implements Entry {
    public static final TechniqueEntry INSTANCE = new TechniqueEntry();
    public static final MapCodec<TechniqueEntry> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean matches(ItemStack stack) {
        return stack.has(MxtDataComponents.TECHNIQUE.get());
    }

    @Override
    public boolean itemLevel() {
        return false;
    }

    @Override
    public MapCodec<TechniqueEntry> codec() {
        return CODEC;
    }
}
