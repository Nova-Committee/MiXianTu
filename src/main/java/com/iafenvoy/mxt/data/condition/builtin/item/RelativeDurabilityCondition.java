package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public record RelativeDurabilityCondition(Comparison comparison) implements ItemCondition {
    public static final MapCodec<RelativeDurabilityCondition> CODEC = Comparison.CODEC.xmap(RelativeDurabilityCondition::new, RelativeDurabilityCondition::comparison);

    @Override
    public boolean test(Entity holder, ItemStack stack, FormulaContext context) {
        return stack.isDamageableItem() && this.comparison.compare((float) (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage());
    }

    @Override
    public MapCodec<RelativeDurabilityCondition> codec() {
        return CODEC;
    }
}
