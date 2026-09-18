package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.data.aura.BlockAura;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.world.FormationAbsorption.Sources;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Rebuilds the bounded, per-chunk cache for datapack-defined aura-emitting blocks.
 */
public final class BlockAuraService {
    private BlockAuraService() {
    }

    /**
     * Scans one chunk column and records every block selected by a {@code block_aura} definition. Block ids
     * and tags are expanded once into a per-block index, so the inner loop does one lookup per block.
     */
    public static void rebuild(ServerLevel level, LevelChunk chunk) {
        Registry<Block> blocks = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        Index index = index(level, blocks);
        if (index.definitions().isEmpty()) {
            chunk.getData(MxtAttachments.AURA_CHUNK).setBlockContribution(List.of());
            return;
        }
        List<BlockAuraContribution> contributions = new ArrayList<>();
        MutableBlockPos pos = new MutableBlockPos();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int minY = level.getMinY();
        int maxY = level.getMaxY();
        // Decided here rather than at query time: the shared stock subtracts this chunk's whole aggregate, so
        // an absorbed emitter left in it would be handed back to every query and spent twice.
        Sources absorbed = Sources.of(level, minX, minZ, minX + 15, minZ + 15);
        for (int x = minX; x < minX + 16; x++) {
            for (int z = minZ; z < minZ + 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    pos.set(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    List<BlockAura> definitions = index.byBlock().get(state.getBlock());
                    if (definitions == null) continue;
                    boolean insideFormation = !absorbed.empty() && absorbed.absorbed(pos);
                    for (BlockAura definition : definitions) {
                        contributions.add(new BlockAuraContribution(pos.immutable(), definition.aura(), insideFormation));
                    }
                }
            }
        }
        chunk.getData(MxtAttachments.AURA_CHUNK).setBlockContribution(contributions);
    }

    /**
     * Expands every definition's block ids and tags into a direct block index.
     */
    private static Index index(ServerLevel level, Registry<Block> blocks) {
        List<BlockAura> definitions = MxtDatapackRegistries.holders(level.registryAccess(), MxtResourceKeys.BLOCK_AURA)
                .map(Reference::value).toList();
        Reference2ObjectMap<Block, List<BlockAura>> byBlock = new Reference2ObjectOpenHashMap<>();
        for (BlockAura definition : definitions) {
            for (Holder<Block> holder : RegistryCodecs.resolve(definition.blocks(), blocks).toList()) {
                Block block = holder.value();
                List<BlockAura> values = byBlock.get(block);
                if (values == null) {
                    values = new ArrayList<>();
                    byBlock.put(block, values);
                }
                values.add(definition);
            }
        }
        return new Index(definitions, byBlock);
    }

    public static boolean matches(ServerLevel level, BlockState state) {
        Block block = state.getBlock();
        Registry<Block> blocks = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        return MxtDatapackRegistries.holders(level.registryAccess(), MxtResourceKeys.BLOCK_AURA)
                .anyMatch(holder -> RegistryCodecs.resolve(holder.value().blocks(), blocks)
                        .anyMatch(candidate -> candidate.value() == block));
    }

    private record Index(List<BlockAura> definitions, Reference2ObjectMap<Block, List<BlockAura>> byBlock) {
    }
}
