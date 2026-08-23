package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.data.aura.AuraMaximum.Fixed;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.data.aura.BlockAura;
import com.iafenvoy.mxt.data.aura.AuraValue;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import com.iafenvoy.mxt.data.resource.Resource;
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
        List<BlockAura> active = MxtDatapackRegistries.holders(level.registryAccess(), MxtResourceKeys.BLOCK_AURA)
                .map(Reference::value).toList();
        Map<Holder<Resource>, AuraValue> aura = new LinkedHashMap<>();
        Set<Identifier> auraKinds = new LinkedHashSet<>();
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
                        definition.aura().forEach((resource, value) -> aura.merge(resource, value, BlockAuraService::merge));
                        auraKinds.addAll(definition.auraKinds());
                    }
                }
        chunk.getData(MxtAttachments.AURA_CHUNK).setBlockContribution(aura, auraKinds);
    }

    private static AuraValue merge(AuraValue first, AuraValue second) {
        double amount = first.amount() + second.amount();
        double maximum = first.max().resolve(first.amount()) + second.max().resolve(second.amount());
        double firstWeight = Math.max(0.0D, first.amount());
        double secondWeight = Math.max(0.0D, second.amount());
        double totalWeight = firstWeight + secondWeight;
        int color = totalWeight <= 0.0D ? first.color() : weightedColor(first.color(), firstWeight, second.color(), secondWeight, totalWeight);
        return new AuraValue(amount, new Fixed(maximum),
                first.regenPerTick() + second.regenPerTick(), color);
    }

    private static int weightedColor(int first, double firstWeight, int second, double secondWeight, double totalWeight) {
        int red = (int) Math.round((((first >>> 16) & 0xFF) * firstWeight + ((second >>> 16) & 0xFF) * secondWeight) / totalWeight);
        int green = (int) Math.round((((first >>> 8) & 0xFF) * firstWeight + ((second >>> 8) & 0xFF) * secondWeight) / totalWeight);
        int blue = (int) Math.round(((first & 0xFF) * firstWeight + (second & 0xFF) * secondWeight) / totalWeight);
        return (red << 16) | (green << 8) | blue;
    }
}
