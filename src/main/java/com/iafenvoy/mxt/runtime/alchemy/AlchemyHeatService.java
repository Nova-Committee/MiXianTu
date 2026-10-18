package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.api.AlchemyHeatSource;
import com.iafenvoy.mxt.data.alchemy.HeatSource;
import com.iafenvoy.mxt.registry.cache.RegistryCaches;
import com.iafenvoy.mxt.util.MiscUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * How hot the block in a furnace's heat cell is. A block implementing {@link AlchemyHeatSource} answers for itself
 * and wins over the table; every other block is looked up in {@code mxt:heat_source}.
 */
public final class AlchemyHeatService {
    public static double maxTemperature(ServerLevel level, BlockPos pos) {
        BlockState state = stateAt(level, pos);
        if (state == null) return 0.0D;
        if (state.getBlock() instanceof AlchemyHeatSource source)
            return MiscUtil.positive(source.maxTemperature(state, level, pos));
        HeatSource definition = find(state.getBlock());
        return definition == null ? 0.0D : MiscUtil.positive(definition.maxTemperature());
    }

    public static double heatingPerTick(ServerLevel level, BlockPos pos) {
        BlockState state = stateAt(level, pos);
        if (state == null) return 0.0D;
        if (state.getBlock() instanceof AlchemyHeatSource source)
            return MiscUtil.positive(source.heatingPerTick(state, level, pos));
        HeatSource definition = find(state.getBlock());
        return definition == null ? 0.0D : MiscUtil.positive(definition.heatingPerTick());
    }

    /**
     * Whether this block claims to heat at all - a table entry, or a block answering for itself. The furnace reads
     * the answer rather than this flag; a display asks it to tell "no heat source" from "one that is currently off".
     */
    public static boolean isHeatSource(BlockState state) {
        return state.getBlock() instanceof AlchemyHeatSource || find(state.getBlock()) != null;
    }

    // The heat cell may sit in a chunk nobody loaded; reading it must not generate one.
    private static @Nullable BlockState stateAt(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) ? level.getBlockState(pos) : null;
    }

    private static @Nullable HeatSource find(Block block) {
        return RegistryCaches.HEAT_SOURCE.get(block);
    }
}
