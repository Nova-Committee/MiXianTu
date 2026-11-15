package com.iafenvoy.mxt.data.cost.context;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record AuraSection(ResourceKey<Level> dimension, int chunkX, int chunkZ, int sectionY) {
    public static AuraSection of(ResourceKey<Level> dimension, BlockPos pos) {
        return new AuraSection(dimension, SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()), SectionPos.blockToSectionCoord(pos.getY()));
    }
}
