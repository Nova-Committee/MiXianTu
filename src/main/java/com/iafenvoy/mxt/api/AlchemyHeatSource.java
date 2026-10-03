package com.iafenvoy.mxt.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block that heats a furnace, for the cases a data pack cannot state: the answer may depend on the block's own
 * state - a lit fire, a filled brazier - or on what stands around it.
 * <p>
 * This is never required. Everything a pack can express goes into the {@code mxt:heat_source} table, which the
 * furnace reads by block; a block that implements this interface answers for itself and overrides its own table
 * entry. Ask for the block's own cell, not the controller: {@code pos} is the cell the heat comes from.
 * <p>
 * Answers are finite and positive, or the furnace treats the heat as absent. Implementations must not allocate a
 * per-tick profile; the workstation reads these two numbers and applies them itself. Reading happens on the server
 * only, and never forces a chunk to load.
 */
public interface AlchemyHeatSource {
    double maxTemperature(BlockState state, ServerLevel level, BlockPos pos);

    double heatingPerTick(BlockState state, ServerLevel level, BlockPos pos);
}
