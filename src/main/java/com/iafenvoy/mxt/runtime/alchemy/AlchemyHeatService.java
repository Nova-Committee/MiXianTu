package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.api.AlchemyHeatSource;
import com.iafenvoy.mxt.data.alchemy.HeatSource;
import com.iafenvoy.mxt.registry.MxtDataMaps;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * How hot the block in a furnace's heat cell is. A block implementing {@link AlchemyHeatSource} answers for itself
 * and wins over the table; every other block is looked up in the {@code mxt:heat_source} data map.
 */
public final class AlchemyHeatService {
    private AlchemyHeatService() {
    }

    public static double maxTemperature(ServerLevel level, BlockPos pos) {
        BlockState state = stateAt(level, pos);
        if (state == null) return 0.0D;
        if (state.getBlock() instanceof AlchemyHeatSource source)
            return positive(source.maxTemperature(state, level, pos));
        HeatSource definition = find(state);
        return definition == null ? 0.0D : positive(definition.maxTemperature());
    }

    public static double heatingPerTick(ServerLevel level, BlockPos pos) {
        BlockState state = stateAt(level, pos);
        if (state == null) return 0.0D;
        if (state.getBlock() instanceof AlchemyHeatSource source)
            return positive(source.heatingPerTick(state, level, pos));
        HeatSource definition = find(state);
        return definition == null ? 0.0D : positive(definition.heatingPerTick());
    }

    /**
     * Whether this block claims to heat at all - a table entry, or a block answering for itself. The furnace reads
     * the answer rather than this flag; a display asks it to tell "no heat source" from "one that is currently off".
     */
    public static boolean isHeatSource(BlockState state) {
        return state.getBlock() instanceof AlchemyHeatSource || find(state) != null;
    }

    // The heat cell may sit in a chunk nobody loaded; reading it must not generate one.
    private static @Nullable BlockState stateAt(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) ? level.getBlockState(pos) : null;
    }

    private static @Nullable HeatSource find(BlockState state) {
        return state.getData(MxtDataMaps.HEAT_SOURCE);
    }

    private static double positive(double value) {
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }
}
