package com.iafenvoy.mxt.util.matcher.builtin;

import com.iafenvoy.mxt.util.matcher.ItemMatcher.Entry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record TagEntry(TagKey<Item> tag) implements Entry {
    public static final MapCodec<TagEntry> CODEC = TagKey.codec(Registries.ITEM).fieldOf("tag").xmap(TagEntry::new, TagEntry::tag);

    @Override
    public boolean matches(ItemStack stack) {
        return stack.is(this.tag);
    }

    @Override
    public MapCodec<TagEntry> codec() {
        return CODEC;
    }
}
