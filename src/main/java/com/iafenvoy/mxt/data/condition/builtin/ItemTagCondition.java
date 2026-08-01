package com.iafenvoy.mxt.data.condition.builtin;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.util.ItemMatcher;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Matches an item stack against a vanilla or datapack item tag. */
public record ItemTagCondition(TagKey<Item> tag) implements ItemCondition, ItemMatcher {
    public static final MapCodec<ItemTagCondition> CODEC = TagKey.hashedCodec(Registries.ITEM).fieldOf("tag").xmap(ItemTagCondition::new, ItemTagCondition::tag);
    @Override public boolean test(Entity holder, ItemStack stack, FormulaContext context) {
        return this.entries().stream().anyMatch(entry -> entry.matches(stack)); }
    @Override public List<ItemMatcher.Entry> entries() { return List.of(ItemMatcher.Entry.tag(this.tag)); }
    @Override public MapCodec<ItemTagCondition> codec() { return CODEC; }
}
