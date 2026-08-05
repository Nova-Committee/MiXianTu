package com.iafenvoy.mxt.data.action.builtin.item;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.util.codec.AutoIgnoreListCodec;
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

import java.util.List;
import java.util.Optional;

public record RemoveEnchantmentAction(List<Holder<Enchantment>> enchantment, Optional<Integer> level,
                                      boolean resetRepairCost) implements ItemAction {
    public static final MapCodec<RemoveEnchantmentAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            AutoIgnoreListCodec.create(Enchantment.CODEC).optionalFieldOf("enchantment", List.of()).forGetter(RemoveEnchantmentAction::enchantment),
            Codec.INT.optionalFieldOf("level").forGetter(RemoveEnchantmentAction::level),
            Codec.BOOL.optionalFieldOf("reset_repair_cost", false).forGetter(RemoveEnchantmentAction::resetRepairCost)
    ).apply(instance, RemoveEnchantmentAction::new));

    public RemoveEnchantmentAction {
        enchantment = List.copyOf(enchantment);
    }

    @Override
    public void execute(Entity holder, ItemStack stack, FormulaContext context) {
        Mutable enchantments = new Mutable(stack.getEnchantments());
        for (Holder<Enchantment> entry : this.enchantment)
            if (this.level.isEmpty() || enchantments.getLevel(entry) == this.level.get()) enchantments.set(entry, 0);
        stack.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        if (this.resetRepairCost) stack.set(DataComponents.REPAIR_COST, 0);
    }

    @Override
    public MapCodec<RemoveEnchantmentAction> codec() {
        return CODEC;
    }
}
