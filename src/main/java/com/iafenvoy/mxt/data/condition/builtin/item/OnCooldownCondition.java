package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

public enum OnCooldownCondition implements ItemCondition {
    INSTANCE;
    public static final MapCodec<OnCooldownCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        Entity holder = ctx.holder();
        ItemStack stack = ctx.stack();
        FormulaContext context = ctx.formula();
        return holder instanceof Player player && player.getCooldowns().isOnCooldown(stack);
    }

    @Override
    public @NonNull MapCodec<OnCooldownCondition> codec() {
        return CODEC;
    }
}
