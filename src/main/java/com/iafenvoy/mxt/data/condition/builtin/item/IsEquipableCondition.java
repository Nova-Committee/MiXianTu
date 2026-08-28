package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.Optional;

public record IsEquipableCondition(Optional<EquipmentSlot> slot) implements ItemCondition {
    public static final MapCodec<IsEquipableCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            EquipmentSlot.CODEC.optionalFieldOf("slot").forGetter(IsEquipableCondition::slot)
    ).apply(i, IsEquipableCondition::new));

    @Override
    public boolean test(ItemConditionContext ctx) {
        Entity holder = ctx.holder();
        ItemStack stack = ctx.stack();
        FormulaContext context = ctx.formula();
        Equippable equipable = stack.get(DataComponents.EQUIPPABLE);
        return equipable != null && this.slot.map(value -> value == equipable.slot()).orElse(true);
    }

    @Override
    public MapCodec<IsEquipableCondition> codec() {
        return CODEC;
    }
}
