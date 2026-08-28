package com.iafenvoy.mxt.data.condition.builtin.item.meta;

import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

public record ConstantCondition(boolean value) implements ItemCondition {
    public static final MapCodec<ConstantCondition> CODEC = Codec.BOOL.fieldOf("value").xmap(ConstantCondition::new, ConstantCondition::value);

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        Entity holder = ctx.holder();
        ItemStack stack = ctx.stack();
        FormulaContext context = ctx.formula();
        return this.value;
    }

    @Override
    public @NonNull MapCodec<ConstantCondition> codec() {
        return CODEC;
    }
}
