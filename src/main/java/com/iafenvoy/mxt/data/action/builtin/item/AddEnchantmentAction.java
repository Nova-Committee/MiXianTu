package com.iafenvoy.mxt.data.action.builtin.item;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments.Mutable;

import java.util.Map;
import java.util.Map.Entry;

/**
 * Adds or upgrades enchantments on the acted item stack.
 */
public record AddEnchantmentAction(Map<Holder<Enchantment>, Integer> enchantments,
                                   boolean override) implements ItemAction {
    public static final MapCodec<AddEnchantmentAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(Enchantment.CODEC, Codec.intRange(1, 255)).fieldOf("enchantments").forGetter(AddEnchantmentAction::enchantments),
            Codec.BOOL.optionalFieldOf("override", false).forGetter(AddEnchantmentAction::override)
    ).apply(instance, AddEnchantmentAction::new));

    public AddEnchantmentAction {
        enchantments = Map.copyOf(enchantments);
    }

    @Override
    public void execute(Entity holder, ItemStack stack, FormulaContext context) {
        Mutable enchantments = new Mutable(stack.getEnchantments());
        for (Entry<Holder<Enchantment>, Integer> entry : this.enchantments.entrySet()) {
            if (this.override || enchantments.getLevel(entry.getKey()) < entry.getValue())
                enchantments.set(entry.getKey(), entry.getValue());
        }
        stack.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
    }

    @Override
    public MapCodec<AddEnchantmentAction> codec() {
        return CODEC;
    }
}
