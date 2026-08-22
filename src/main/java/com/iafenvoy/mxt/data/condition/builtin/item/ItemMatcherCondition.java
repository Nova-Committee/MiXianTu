package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Matches any item or item tag in one entry. The {@code items} field accepts a
 * single value or an array, and the array may freely mix item ids and tags.
 */
public record ItemMatcherCondition(List<Entry> entries) implements ItemCondition, ItemMatcher {
    public static final MapCodec<ItemMatcherCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ENTRIES_CODEC.fieldOf("items").forGetter(ItemMatcherCondition::entries)
    ).apply(i, ItemMatcherCondition::new));

    public ItemMatcherCondition {
        if (entries.isEmpty()) throw new IllegalArgumentException("items must not be empty");
    }

    @Override
    public boolean test(Entity holder, ItemStack stack, FormulaContext context) {
        return this.entries().stream().anyMatch(entry -> entry.matches(stack));
    }

    @Override
    public MapCodec<ItemMatcherCondition> codec() {
        return CODEC;
    }
}
