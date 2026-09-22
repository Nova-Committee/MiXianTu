package com.iafenvoy.mxt.runtime.spirit;

import com.iafenvoy.mxt.api.ItemAuraAccess;
import com.iafenvoy.mxt.util.matcher.ItemMatcher.Entry;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Matches any item implementing {@link ItemAuraAccess}: a capability rather than an id, so an item added later
 * is covered without touching the file that declared it. The codec is a unit, so {@code type} carries the whole
 * declaration: {@code {"type": "mxt:spirit_storage"}}.
 */
public record SpiritStorageEntry() implements Entry {
    public static final SpiritStorageEntry INSTANCE = new SpiritStorageEntry();
    public static final MapCodec<SpiritStorageEntry> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean matches(ItemStack stack) {
        return stack.getItem() instanceof ItemAuraAccess;
    }

    @Override
    public MapCodec<SpiritStorageEntry> codec() {
        return CODEC;
    }
}
