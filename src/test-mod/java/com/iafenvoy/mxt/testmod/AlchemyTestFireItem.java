package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.api.AlchemyHeatSource;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Deterministic test fire. It is not consumed and has no production recipe. */
public final class AlchemyTestFireItem extends Item implements AlchemyHeatSource {
    private final double maxTemperature;
    private final double heatingPerTick;

    public AlchemyTestFireItem(Properties properties, double maxTemperature, double heatingPerTick) {
        super(properties);
        this.maxTemperature = maxTemperature;
        this.heatingPerTick = heatingPerTick;
    }

    @Override
    public double maxTemperature(ItemStack stack, ServerLevel level, BlockPos pos) {
        return this.maxTemperature;
    }

    @Override
    public double heatingPerTick(ItemStack stack, ServerLevel level, BlockPos pos) {
        return this.heatingPerTick;
    }
}
