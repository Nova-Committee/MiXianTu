package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.aura.SpiritStorageTooltipAppender;
import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Matches chargeable items whose stored spirit power is below their capacity.
 */
public enum SpiritStorageNotFullCondition implements ItemCondition {
    INSTANCE;

    public static final MapCodec<SpiritStorageNotFullCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(Entity holder, ItemStack stack, FormulaContext context) {
        return SpiritStorageTooltipAppender.resolveCharge(holder.level().registryAccess(), stack, context)
                .map(charge -> charge.stored() < charge.capacity()).orElse(false);
    }

    @Override
    public MapCodec<SpiritStorageNotFullCondition> codec() {
        return CODEC;
    }
}
