package com.iafenvoy.mxt.data.condition;

import com.iafenvoy.mxt.data.condition.builtin.item.meta.AndItemCondition;
import com.iafenvoy.mxt.data.context.Context;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public interface ItemCondition {
    Codec<ItemCondition> SINGLE_CODEC = MxtRegistries.ITEM_CONDITION_TYPE.byNameCodec().dispatch("type", ItemCondition::codec, Function.identity());
    Codec<ItemCondition> CODEC = Codec.either(SINGLE_CODEC.listOf(), SINGLE_CODEC).xmap(value -> value.map(AndItemCondition::new, Function.identity()), Either::right);

    static MapCodec<ItemCondition> optionalCodec(String name) {
        return CODEC.optionalFieldOf(name, AlwaysTrueCondition.INSTANCE);
    }

    @NotNull MapCodec<? extends ItemCondition> codec();

    boolean test(@NotNull ItemConditionContext context);

    default boolean test(Entity holder, ItemStack stack, Context parent) {
        return this.test(parent.copyTo(new ItemConditionContext(holder, stack, parent.formula())));
    }

    default boolean test(Entity holder, ItemStack stack, FormulaContext formula) {
        return this.test(new ItemConditionContext(holder, stack, formula));
    }
}
