package com.iafenvoy.mxt.util.matcher.builtin;

import com.iafenvoy.mxt.util.matcher.ItemMatcher.Entry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record ItemEntry(Item item) implements Entry {
    public static final MapCodec<ItemEntry> CODEC = BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").xmap(ItemEntry::new, ItemEntry::item);

    @Override
    public boolean matches(ItemStack stack) {
        return stack.is(this.item);
    }

    @Override
    public MapCodec<ItemEntry> codec() {
        return CODEC;
    }
}
