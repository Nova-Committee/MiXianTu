package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public enum OnCooldownCondition implements ItemCondition {
    INSTANCE;
    public static final MapCodec<OnCooldownCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(Entity holder, ItemStack stack, FormulaContext context) {
        return holder instanceof Player player && player.getCooldowns().isOnCooldown(stack);
    }

    @Override
    public MapCodec<OnCooldownCondition> codec() {
        return CODEC;
    }
}
