package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.data.formation.Formation.RequiredBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

public record FormationPreview(List<RequiredBlock> blocks, int width, int height, int depth) {
    public static final int MAX_BLOCKS = 4096;
    public static final int MAX_SPAN = 64;
    public static final int BASE_PLATE_BORDER = 1;

    public FormationPreview {
        blocks = List.copyOf(blocks);
    }

    public static Optional<FormationPreview> prepare(List<RequiredBlock> structure) {
        if (structure.isEmpty() || structure.size() > MAX_BLOCKS) return Optional.empty();
        LinkedHashMap<BlockPos, RequiredBlock> states = new LinkedHashMap<>();
        for (RequiredBlock block : structure) states.put(block.offset(), block);
        List<RequiredBlock> blocks = states.values().stream().filter(block -> !block.state().isAir()).toList();
        if (blocks.isEmpty()) return Optional.empty();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (RequiredBlock block : blocks) {
            BlockPos pos = block.offset();
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        long width = (long) maxX - minX + 1, height = (long) maxY - minY + 1, depth = (long) maxZ - minZ + 1;
        if (width > MAX_SPAN || height > MAX_SPAN || depth > MAX_SPAN) return Optional.empty();
        int size = (int) Math.max(width, depth);
        int originX = minX, originY = minY, originZ = minZ;
        // Ponder reserves y=0 for its base plate; centre rectangular shapes within its square footprint.
        List<RequiredBlock> normalized = blocks.stream().map(block -> new RequiredBlock(new BlockPos(
                (int) ((long) block.offset().getX() - originX) + (size - (int) width) / 2,
                (int) ((long) block.offset().getY() - originY) + 1,
                (int) ((long) block.offset().getZ() - originZ) + (size - (int) depth) / 2), block.state())).toList();
        return Optional.of(new FormationPreview(normalized, (int) width, (int) height, (int) depth));
    }

    public int basePlateSize() {
        return Math.max(this.width, this.depth) + 2 * BASE_PLATE_BORDER;
    }

    public List<RequiredBlock> displayBlocks() {
        int size = this.basePlateSize();
        ArrayList<RequiredBlock> display = new ArrayList<>(this.blocks.size() + size * size);
        for (int x = -BASE_PLATE_BORDER; x < size - BASE_PLATE_BORDER; x++)
            for (int z = -BASE_PLATE_BORDER; z < size - BASE_PLATE_BORDER; z++)
                display.add(new RequiredBlock(new BlockPos(x, 0, z),
                        ((x + z) % 2 == 0 ? Blocks.SNOW_BLOCK : Blocks.WHITE_CONCRETE).defaultBlockState()));
        display.addAll(this.blocks);
        return List.copyOf(display);
    }
}
