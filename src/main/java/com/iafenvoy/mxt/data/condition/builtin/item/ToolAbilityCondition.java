package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ItemAbility;

public record ToolAbilityCondition(ItemAbility ability) implements ItemCondition {
    public static final MapCodec<ToolAbilityCondition> CODEC = ItemAbility.CODEC.fieldOf("ability").xmap(ToolAbilityCondition::new, ToolAbilityCondition::ability);

    @Override
    public boolean test(Entity holder, ItemStack stack, FormulaContext context) {
        return stack.canPerformAction(this.ability);
    }

    @Override
    public MapCodec<ToolAbilityCondition> codec() {
        return CODEC;
    }
}
