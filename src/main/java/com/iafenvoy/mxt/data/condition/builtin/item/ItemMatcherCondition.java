package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * The {@code items} field takes one entry or an array, and the array may freely mix item ids and tags.
 */
public record ItemMatcherCondition(List<Entry> entries) implements ItemCondition, ItemMatcher {
    public static final MapCodec<ItemMatcherCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ENTRIES_CODEC.fieldOf("items").forGetter(ItemMatcherCondition::entries)
    ).apply(i, ItemMatcherCondition::new));

    public ItemMatcherCondition {
        if (entries.isEmpty()) throw new IllegalArgumentException("items must not be empty");
    }

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        return this.entries().stream().anyMatch(entry -> entry.matches(ctx.stack()));
    }

    @Override
    public int priority() {
        return DEFAULT_PRIORITY;
    }

    @Override
    public @NonNull MapCodec<ItemMatcherCondition> codec() {
        return CODEC;
    }
}
