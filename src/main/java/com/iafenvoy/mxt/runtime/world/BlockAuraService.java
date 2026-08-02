package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.data.aura.BlockAura;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rebuilds the bounded, per-chunk cache for datapack-defined aura-emitting blocks.
 */
public final class BlockAuraService {
    private BlockAuraService() {
    }

    public static void rebuild(ServerLevel level, LevelChunk chunk) {
        List<BlockAura> active = MxtDatapackRegistries.holders(level.registryAccess(), MxtDatapackRegistries.BLOCK_AURA)
                .map(Reference::value).toList();
        double aura = 0.0D;
        double regen = 0.0D;
        Map<Identifier, Double> elements = new LinkedHashMap<>();
        Set<Identifier> tags = new LinkedHashSet<>();
        MutableBlockPos pos = new MutableBlockPos();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        for (int x = minX; x < minX + 16; x++)
            for (int z = minZ; z < minZ + 16; z++)
                for (int y = level.getMinY(); y < level.getMaxY(); y++) {
                    pos.set(x, y, z);
                    Identifier id = BuiltInRegistries.BLOCK.getKey(chunk.getBlockState(pos).getBlock());
                    for (BlockAura definition : active) {
                        if (!RegistryCodecs.matches(definition.blocks(), BuiltInRegistries.BLOCK, Registries.BLOCK, id))
                            continue;
                        aura += definition.auraPerBlock();
                        regen += definition.regenPerTickPerBlock();
                        definition.elementAuraPerBlock().forEach((element, value) -> elements.merge(element, value, Double::sum));
                        tags.addAll(definition.environmentTags());
                    }
                }
        chunk.getData(MxtAttachments.AURA_CHUNK).setBlockContribution(aura, regen, elements, tags);
    }
}
