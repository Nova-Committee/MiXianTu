package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.iafenvoy.mxt.util.matcher.builtin.ItemEntry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record ItemIdCondition(Item item) implements ItemCondition, ItemMatcher {
    public static final MapCodec<ItemIdCondition> CODEC = BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").xmap(ItemIdCondition::new, ItemIdCondition::item);

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        Entity holder = ctx.holder();
        ItemStack stack = ctx.stack();
        FormulaContext context = ctx.formula();
        return this.entries().stream().anyMatch(entry -> entry.matches(stack));
    }

    @Override
    public List<Entry> entries() {
        return List.of(new ItemEntry(this.item));
    }

    @Override
    public @NonNull MapCodec<ItemIdCondition> codec() {
        return CODEC;
    }
}
