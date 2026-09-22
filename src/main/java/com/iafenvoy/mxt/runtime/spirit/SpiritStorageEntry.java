package com.iafenvoy.mxt.runtime.spirit;

import com.iafenvoy.mxt.api.ItemAuraAccess;
import com.iafenvoy.mxt.util.matcher.ItemMatcher.Entry;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Matches any item that implements {@link ItemAuraAccess}: a capability rather than an id, so a declaration can
 * say "an item that stores resources" wherever it matches items, and an item added later is covered without
 * touching the file that declared it. The storing half is the right one to match on, since everything poured
 * into is stored in.
 * <p>
 * It lives beside the interface it names rather than with the other built-in entries, because it is the one entry
 * that depends on this package instead of on vanilla's item registry alone. {@code type} carries it: the codec is
 * a unit, so {@code {"type": "mxt:spirit_storage"}} is the whole of it.
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
