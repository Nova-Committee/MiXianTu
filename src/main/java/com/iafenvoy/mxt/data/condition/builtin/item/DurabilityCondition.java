package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public record DurabilityCondition(Comparison comparison) implements ItemCondition {
    public static final MapCodec<DurabilityCondition> CODEC = Comparison.CODEC.xmap(DurabilityCondition::new, DurabilityCondition::comparison);

    @Override
    public boolean test(Entity holder, ItemStack stack, FormulaContext context) {
        return stack.isDamageableItem() && this.comparison.compare(stack.getMaxDamage() - stack.getDamageValue());
    }

    @Override
    public MapCodec<DurabilityCondition> codec() {
        return CODEC;
    }
}
