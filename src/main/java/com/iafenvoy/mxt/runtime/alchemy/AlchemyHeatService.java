package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.api.AlchemyHeatSource;
import com.iafenvoy.mxt.data.alchemy.HeatSource;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How hot the block in a furnace's heat cell is. A block implementing {@link AlchemyHeatSource} answers for itself
 * and wins over the table; every other block is looked up in {@code mxt:heat_source}.
 */
public final class AlchemyHeatService {
    private static final Map<Block, HeatSource> INDEX_CACHE = new LinkedHashMap<>();

    public static void clear() {
        INDEX_CACHE.clear();
    }

    public static void reload(RegistryAccess access) {
        clear();
        access.lookupOrThrow(MxtResourceKeys.HEAT_SOURCE).listElements().forEach(holder -> claim(holder.value()));
    }

    private static void claim(HeatSource definition) {
        for (Holder<Block> holder : RegistryCodecs.resolve(definition.blocks(), BuiltInRegistries.BLOCK).toList()) {
            HeatSource existing = INDEX_CACHE.get(holder.value());
            if (existing == null || definition.priority() > existing.priority())
                INDEX_CACHE.put(holder.value(), definition);
        }
    }

    public static double maxTemperature(ServerLevel level, BlockPos pos) {
        BlockState state = stateAt(level, pos);
        if (state == null) return 0.0D;
        if (state.getBlock() instanceof AlchemyHeatSource source)
            return positive(source.maxTemperature(state, level, pos));
        HeatSource definition = find(state.getBlock());
        return definition == null ? 0.0D : positive(definition.maxTemperature());
    }

    public static double heatingPerTick(ServerLevel level, BlockPos pos) {
        BlockState state = stateAt(level, pos);
        if (state == null) return 0.0D;
        if (state.getBlock() instanceof AlchemyHeatSource source)
            return positive(source.heatingPerTick(state, level, pos));
        HeatSource definition = find(state.getBlock());
        return definition == null ? 0.0D : positive(definition.heatingPerTick());
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
        return INDEX_CACHE.get(block);
    }

    private static double positive(double value) {
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }
}
