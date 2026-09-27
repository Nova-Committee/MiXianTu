package com.iafenvoy.mxt.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * An item that can occupy a furnace fire slot. Answers are finite and positive, or the furnace treats the fire as absent.
 * Implementations must not allocate a per-tick profile; the workstation reads these two numbers and applies them itself.
 */
public interface AlchemyHeatSource {
    double maxTemperature(ItemStack stack, ServerLevel level, BlockPos pos);

    double heatingPerTick(ItemStack stack, ServerLevel level, BlockPos pos);
}
