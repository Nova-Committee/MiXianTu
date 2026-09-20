package com.iafenvoy.mxt.runtime.rift;

import com.iafenvoy.mxt.item.block.entity.RiftBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Connectivity of a rift.
 *
 * <p>A rift looks at the 3x3x3 blocks around itself and links to every rift it finds there, whichever way those
 * rifts are turned: a link is only ever a line between two neighbouring points, so there is nothing for a
 * direction to decide. That reach is also why the graph is a graph and not a lattice: two rifts one block apart
 * on a diagonal are as linked as two that share a face, which is what lets three of them close a triangle.
 *
 * <p>Those links are what a rift is drawn from - a point per block, a beam per link and a filled triangle per
 * closed loop - and what {@link #componentSize} walks when the command reports how far one rift's network
 * reaches.
 */
public final class RiftConnections {
    /**
     * Upper bound on a component walk. A command must not be able to hang on a network that spans a whole world.
     */
    public static final int MAX_COMPONENT = 512;
    private static final List<BlockPos> OFFSETS = buildOffsets();

    private RiftConnections() {
    }

    /**
     * A closed three-block loop seen from one of its three rifts: the two partners other than that rift, in the
     * order the three of them sort into, so that all three agree on which share is whose.
     */
    public record Loop(BlockPos forward, BlockPos backward) {
    }

    /**
     * The twenty-six neighbour offsets of a 3x3x3 block, in a stable order.
     */
    public static List<BlockPos> offsets() {
        return OFFSETS;
    }

    /**
     * Every rift in the 3x3x3 blocks around {@code pos}, as absolute positions. Chunks that are not loaded are
     * skipped rather than loaded, so looking at a rift never generates terrain.
     */
    public static List<BlockPos> connected(Level level, BlockPos pos) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos offset : OFFSETS) {
            BlockPos other = pos.offset(offset);
            if (!level.isLoaded(other)) continue;
            if (level.getBlockEntity(other) instanceof RiftBlockEntity) found.add(other);
        }
        return found;
    }

    /**
     * Whether this rift is linked to nothing at all, so that all that is drawn of it is its own point.
     */
    public static boolean isolated(Level level, BlockPos pos) {
        for (BlockPos offset : OFFSETS) {
            BlockPos other = pos.offset(offset);
            if (!level.isLoaded(other)) continue;
            if (level.getBlockEntity(other) instanceof RiftBlockEntity) return false;
        }
        return true;
    }

    /**
     * The closed three-block loops {@code self} takes part in, given the links {@link #connected} just returned.
     *
     * <p>Two of a rift's links close a loop when the two are neighbours of each other as well, which is the only
     * test left to make here: the offset decides adjacency and nothing else has a say, so the pairs are settled
     * with arithmetic and no second look at the world. The partners are handed back in the order the three
     * blocks sort into, which is the same cycle seen from each of the three, so the shares the three of them
     * draw tile the triangle instead of overlapping.
     */
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

    /**
     * How many rifts are reachable from {@code origin} by links, including itself, capped at
     * {@link #MAX_COMPONENT}. Diagnostics only, for the command that reports how far one rift's network reaches.
     */
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

    /**
     * Two blocks of a 3x3x3 neighbourhood of each other, which is the same reach a link itself uses.
     */
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
