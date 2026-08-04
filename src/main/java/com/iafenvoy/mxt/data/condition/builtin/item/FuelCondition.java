package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public record FuelCondition(Comparison comparison) implements ItemCondition {
    public static final MapCodec<FuelCondition> CODEC = Comparison.CODEC.xmap(FuelCondition::new, FuelCondition::comparison);

    @Override
    public boolean test(Entity holder, ItemStack stack, FormulaContext context) {
        return this.comparison.compare(holder.level().fuelValues().burnDuration(stack));
    }

    @Override
    public MapCodec<FuelCondition> codec() {
        return CODEC;
    }
}
