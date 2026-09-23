package com.iafenvoy.mxt.runtime.rift;

import com.iafenvoy.mxt.item.block.entity.RiftBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * Connectivity of a rift. A rift looks at the 3x3x3 blocks around itself and links to every rift it finds there,
 * whichever way those rifts are turned: a link is only a line between two neighbouring points, so there is
 * nothing for a direction to decide. Diagonal neighbours link exactly like face-sharing ones, which is what lets
 * three rifts close a triangle.
 */
public final class RiftConnections {
    // Cap on a component walk, so a command cannot hang on a network spanning a whole world.
    public static final int MAX_COMPONENT = 512;
    private static final List<BlockPos> OFFSETS = buildOffsets();

    private RiftConnections() {
    }

    // The two partners other than this rift, in the order the three of them sort into, so all three agree on
    // which share is whose.
    public record Loop(BlockPos forward, BlockPos backward) {
    }

    public static List<BlockPos> offsets() {
        return OFFSETS;
    }

    // Unloaded chunks are skipped rather than loaded, so looking at a rift never generates terrain.
    public static List<BlockPos> connected(Level level, BlockPos pos) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos offset : OFFSETS) {
            BlockPos other = pos.offset(offset);
            if (!level.isLoaded(other)) continue;
            if (level.getBlockEntity(other) instanceof RiftBlockEntity) found.add(other);
        }
        return found;
    }

    public static boolean isolated(Level level, BlockPos pos) {
        for (BlockPos offset : OFFSETS) {
            BlockPos other = pos.offset(offset);
            if (!level.isLoaded(other)) continue;
            if (level.getBlockEntity(other) instanceof RiftBlockEntity) return false;
        }
        return true;
    }

    // The partners are handed back in the order the three blocks sort into, which is the same cycle seen from
    // each of the three, so the shares the three of them draw tile the triangle instead of overlapping.
    public static List<Loop> loops(BlockPos self, List<BlockPos> links) {
        List<Loop> found = new ArrayList<>();
        for (int first = 0; first < links.size(); first++)
            for (int second = first + 1; second < links.size(); second++) {
                BlockPos one = links.get(first);
                BlockPos other = links.get(second);
                if (!adjacent(one, other)) continue;
                BlockPos[] triple = {self, one, other};
                Arrays.sort(triple);
                int index = 0;
                while (index < triple.length && !triple[index].equals(self)) index++;
                found.add(new Loop(triple[(index + 1) % 3], triple[(index + 2) % 3]));
            }
        return found;
    }

    // Diagnostics only, for the command that reports how far one rift's network reaches.
    public static int componentSize(Level level, BlockPos origin) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = origin.immutable();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && seen.size() < MAX_COMPONENT) {
            BlockPos current = queue.poll();
            for (BlockPos offset : OFFSETS) {
                BlockPos next = current.offset(offset);
                if (seen.contains(next) || !level.isLoaded(next)) continue;
                if (level.getBlockEntity(next) instanceof RiftBlockEntity) {
                    seen.add(next.immutable());
                    queue.add(next.immutable());
                }
            }
        }
        return seen.size();
    }

    // The same reach a link itself uses.
    private static boolean adjacent(BlockPos one, BlockPos other) {
        return Math.abs(one.getX() - other.getX()) <= 1
                && Math.abs(one.getY() - other.getY()) <= 1
                && Math.abs(one.getZ() - other.getZ()) <= 1
                && !one.equals(other);
    }

    private static List<BlockPos> buildOffsets() {
        List<BlockPos> offsets = new ArrayList<>(26);
        for (int x = -1; x <= 1; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++)
                    if (x != 0 || y != 0 || z != 0) offsets.add(new BlockPos(x, y, z));
        return List.copyOf(offsets);
    }
}
