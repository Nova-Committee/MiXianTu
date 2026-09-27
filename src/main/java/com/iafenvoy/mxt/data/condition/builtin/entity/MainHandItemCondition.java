package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

/**
 * Asks an item condition about the item in the entity's main hand; an empty hand is false.
 */
public record MainHandItemCondition(ItemCondition itemCondition) implements EntityCondition {
    public static final MapCodec<MainHandItemCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemCondition.optionalCodec("item_condition").forGetter(MainHandItemCondition::itemCondition)
    ).apply(i, MainHandItemCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        if (!(ctx.entity() instanceof LivingEntity living)) return false;
        ItemStack stack = living.getMainHandItem();
        return !stack.isEmpty() && this.itemCondition.test(living, stack, ctx);
    }

    @Override
    public @NonNull MapCodec<MainHandItemCondition> codec() {
        return CODEC;
    }
}
