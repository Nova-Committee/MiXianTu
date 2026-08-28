package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

public record FuelCondition(Comparison comparison) implements ItemCondition {
    public static final MapCodec<FuelCondition> CODEC = Comparison.CODEC.xmap(FuelCondition::new, FuelCondition::comparison);

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        Entity holder = ctx.holder();
        ItemStack stack = ctx.stack();
        FormulaContext context = ctx.formula();
        return this.comparison.compare(stack.getBurnTime(null, holder.level().fuelValues()));
    }

    @Override
    public @NonNull MapCodec<FuelCondition> codec() {
        return CODEC;
    }
}
