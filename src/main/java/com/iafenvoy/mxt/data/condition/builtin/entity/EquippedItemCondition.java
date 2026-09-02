package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

public record EquippedItemCondition(EquipmentSlot equipmentSlot,
                                    ItemCondition itemCondition) implements EntityCondition {
    public static final MapCodec<EquippedItemCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            EquipmentSlot.CODEC.fieldOf("equipment_slot").forGetter(EquippedItemCondition::equipmentSlot),
            ItemCondition.optionalCodec("item_condition").forGetter(EquippedItemCondition::itemCondition)
    ).apply(i, EquippedItemCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        if (!(entity instanceof LivingEntity living)) return false;
        ItemStack stack = living.getItemBySlot(this.equipmentSlot);
        return !stack.isEmpty() && this.itemCondition.test(entity, stack, ctx);
    }

    @Override
    public @NonNull MapCodec<EquippedItemCondition> codec() {
        return CODEC;
    }
}
