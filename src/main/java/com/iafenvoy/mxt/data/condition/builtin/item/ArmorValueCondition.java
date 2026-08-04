package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;

public record ArmorValueCondition(Comparison comparison) implements ItemCondition {
    public static final MapCodec<ArmorValueCondition> CODEC = Comparison.CODEC.xmap(ArmorValueCondition::new, ArmorValueCondition::comparison);

    @Override
    public boolean test(Entity holder, ItemStack stack, FormulaContext context) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null) return false;
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        return this.comparison.compare(modifiers.compute(Attributes.ARMOR, 0.0D, equippable.slot()));
    }

    @Override
    public MapCodec<ArmorValueCondition> codec() {
        return CODEC;
    }
}
