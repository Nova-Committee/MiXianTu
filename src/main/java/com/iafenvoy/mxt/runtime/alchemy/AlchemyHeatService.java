package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.api.AlchemyHeatSource;
import com.iafenvoy.mxt.data.alchemy.HeatSource;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * How hot the block in a furnace's heat cell is. A block implementing {@link AlchemyHeatSource} answers for itself
 * and wins over the table; every other block is looked up in {@code mxt:heat_source}.
 */
public final class AlchemyHeatService {
    private static final int MAX_CACHED_REGISTRIES = 4;
    private static final Object LOCK = new Object();
    // Keyed on the block registry instance, and dropped on every datapack load by ServerCache: a reloaded pack may
    // keep the same registry instance, so the key alone cannot be trusted to notice one. The index expands block
    // tags, so it never outlives the pack that built it. MAX_CACHED_REGISTRIES bounds the map.
    private static Map<Registry<Block>, Map<Block, HeatSource>> indexes = Map.of();

    private AlchemyHeatService() {
    }

    // Called on every datapack load, before anything can rebuild the index.
    public static void invalidate() {
        synchronized (LOCK) {
            indexes = Map.of();
        }
    }

    public static double maxTemperature(ServerLevel level, BlockPos pos) {
        BlockState state = stateAt(level, pos);
        if (state == null) return 0.0D;
        if (state.getBlock() instanceof AlchemyHeatSource source) return positive(source.maxTemperature(state, level, pos));
        HeatSource definition = find(level, state.getBlock());
        return definition == null ? 0.0D : positive(definition.maxTemperature());
    }

    public static double heatingPerTick(ServerLevel level, BlockPos pos) {
        BlockState state = stateAt(level, pos);
        if (state == null) return 0.0D;
        if (state.getBlock() instanceof AlchemyHeatSource source) return positive(source.heatingPerTick(state, level, pos));
        HeatSource definition = find(level, state.getBlock());
        return definition == null ? 0.0D : positive(definition.heatingPerTick());
    }

    // The heat cell may sit in a chunk nobody loaded; reading it must not generate one.
    private static @Nullable BlockState stateAt(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) ? level.getBlockState(pos) : null;
    }

    private static @Nullable HeatSource find(ServerLevel level, Block block) {
        return index(level).get(block);
    }

    private static double positive(double value) {
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }

    private static Map<Block, HeatSource> index(ServerLevel level) {
        Registry<Block> blocks = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        Map<Block, HeatSource> cached = indexes.get(blocks);
        if (cached != null) return cached;
        synchronized (LOCK) {
            cached = indexes.get(blocks);
            if (cached != null) return cached;
            Map<Block, HeatSource> built = new HashMap<>();
            for (Reference<HeatSource> holder : MxtDatapackRegistries.holders(level.registryAccess(), MxtResourceKeys.HEAT_SOURCE).toList())
                claim(built, blocks, holder.value());
            Map<Registry<Block>, Map<Block, HeatSource>> updated =
                    indexes.size() + 1 > MAX_CACHED_REGISTRIES ? new HashMap<>() : new HashMap<>(indexes);
            updated.put(blocks, Map.copyOf(built));
            indexes = Map.copyOf(updated);
            return updated.get(blocks);
        }
    }

    // Registry order is the tie-breaker, so the first definition at a given priority is the one that stays.
    private static void claim(Map<Block, HeatSource> index, Registry<Block> blocks, HeatSource definition) {
        for (Holder<Block> holder : RegistryCodecs.resolve(definition.blocks(), blocks).toList()) {
            HeatSource existing = index.get(holder.value());
            if (existing == null || definition.priority() > existing.priority()) index.put(holder.value(), definition);
        }
    }
}
