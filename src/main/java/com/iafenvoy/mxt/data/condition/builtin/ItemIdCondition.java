package com.iafenvoy.mxt.data.condition.builtin;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.util.ItemMatcher;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record ItemIdCondition(Item item) implements ItemCondition, ItemMatcher {
    public static final MapCodec<ItemIdCondition> CODEC = BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").xmap(ItemIdCondition::new, ItemIdCondition::item);

    @Override
    public boolean test(Entity holder, ItemStack stack, FormulaContext context) {
        return this.entries().stream().anyMatch(entry -> entry.matches(stack));
    }

    @Override
    public List<ItemMatcher.Entry> entries() {
        return List.of(ItemMatcher.Entry.item(this.item));
    }

    @Override
    public MapCodec<ItemIdCondition> codec() {
        return CODEC;
    }
}
