package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

public record RelativeDurabilityCondition(Comparison comparison) implements ItemCondition {
    public static final MapCodec<RelativeDurabilityCondition> CODEC = Comparison.CODEC.xmap(RelativeDurabilityCondition::new, RelativeDurabilityCondition::comparison);

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        Entity holder = ctx.holder();
        ItemStack stack = ctx.stack();
        FormulaContext context = ctx.formula();
        return stack.isDamageableItem() && this.comparison.compare((float) (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage());
    }

    @Override
    public @NonNull MapCodec<RelativeDurabilityCondition> codec() {
        return CODEC;
    }
}
