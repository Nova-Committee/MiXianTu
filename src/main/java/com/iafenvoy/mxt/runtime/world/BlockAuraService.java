package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.attachment.AuraChunkAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraValue;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataMaps;
import com.iafenvoy.mxt.runtime.world.FormationAbsorption.Sources;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.registries.datamaps.DataMapValueMerger;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rebuilds the bounded, per-chunk cache for datapack-defined aura-emitting blocks.
 */
public final class BlockAuraService {
    private BlockAuraService() {
    }

    public static void rebuild(ServerLevel level, LevelChunk chunk) {
        List<BlockAuraContribution> contributions = new ArrayList<>();
        MutableBlockPos pos = new MutableBlockPos();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int minY = level.getMinY();
        int maxY = level.getMaxY();
        // The shared stock subtracts this chunk's whole aggregate, so an absorbed emitter left in it would be
        // handed back to every query and spent twice.
        Sources absorbed = Sources.of(level, minX, minZ, minX + 15, minZ + 15);
        for (int x = minX; x < minX + 16; x++) {
            for (int z = minZ; z < minZ + 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    pos.set(x, y, z);
                    Map<Holder<Aura>, AuraValue> aura = emitted(chunk.getBlockState(pos));
                    if (aura == null) continue;
                    boolean insideFormation = !absorbed.empty() && absorbed.absorbed(pos);
                    contributions.add(new BlockAuraContribution(pos.immutable(), aura, insideFormation));
                }
            }
        }
        chunk.getData(MxtAttachments.AURA_CHUNK).setBlockContribution(contributions);
    }

    public static boolean matches(ServerLevel level, BlockState state) {
        return emitted(state) != null;
    }

    private static @Nullable Map<Holder<Aura>, AuraValue> emitted(BlockState state) {
        return state.getData(MxtDataMaps.BLOCK_AURA);
    }

    /**
     * Blocks accumulate the way the per-chunk aggregate does: a block matched by two entries emits both, so the
     * merge rule is the aggregate's own {@link AuraChunkAttachment#merge}, not "the later value wins".
     */
    public record AuraMerger() implements DataMapValueMerger<Block, Map<Holder<Aura>, AuraValue>> {
        @Override
        public Map<Holder<Aura>, AuraValue> merge(@NonNull Registry<Block> registry, @NonNull Either<TagKey<Block>, ResourceKey<Block>> first,
                                                  Map<Holder<Aura>, AuraValue> firstValue,
                                                  @NonNull Either<TagKey<Block>, ResourceKey<Block>> second,
                                                  Map<Holder<Aura>, AuraValue> secondValue) {
            Map<Holder<Aura>, AuraValue> merged = new LinkedHashMap<>(firstValue);
            secondValue.forEach((aura, value) -> merged.merge(aura, value, AuraChunkAttachment::merge));
            return Map.copyOf(merged);
        }
    }
}
