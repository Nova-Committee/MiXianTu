package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

public record BaseEnchantmentCondition(Holder<Enchantment> enchantment,
                                       Comparison comparison) implements ItemCondition {
    public static final MapCodec<BaseEnchantmentCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Enchantment.CODEC.fieldOf("enchantment").forGetter(BaseEnchantmentCondition::enchantment),
            Comparison.CODEC.forGetter(BaseEnchantmentCondition::comparison)
    ).apply(i, BaseEnchantmentCondition::new));

    @Override
    public boolean test(Entity holder, ItemStack stack, FormulaContext context) {
        return this.comparison.compare(stack.getAllEnchantments(holder.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)).getLevel(this.enchantment));
    }

    @Override
    public MapCodec<BaseEnchantmentCondition> codec() {
        return CODEC;
    }
}
