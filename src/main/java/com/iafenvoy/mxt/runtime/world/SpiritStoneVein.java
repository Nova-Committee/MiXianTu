package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.registry.MxtBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Calculates vein quality from connected spirit-stone ore blocks, rather than storing a fragile block-entity grade.
 */
public final class SpiritStoneVein {
    private static final int MAX_SCAN = 4_096;

    private SpiritStoneVein() {
    }

    public static Result inspect(Level level, BlockPos origin) {
        if (!level.getBlockState(origin).is(MxtBlocks.SPIRIT_STONE_ORE.get())) return new Result(0, Grade.NONE);
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin.immutable());
        while (!queue.isEmpty() && visited.size() < MAX_SCAN) {
            BlockPos pos = queue.removeFirst();
            if (!visited.add(pos) || !level.getBlockState(pos).is(MxtBlocks.SPIRIT_STONE_ORE.get())) continue;
            for (Direction direction : Direction.values()) queue.add(pos.relative(direction));
        }
        return new Result(visited.size(), Grade.fromBlocks(visited.size()));
    }

    public enum Grade {
        NONE(0), POOR(1), COMMON(9), RICH(25), SPIRIT_VEIN(64), HEAVENLY(128);
        private final int minimumBlocks;

        Grade(int minimumBlocks) {
            this.minimumBlocks = minimumBlocks;
        }

        public int minimumBlocks() {
            return this.minimumBlocks;
        }

        static Grade fromBlocks(int blocks) {
            Grade result = NONE;
            for (Grade grade : values()) if (blocks >= grade.minimumBlocks) result = grade;
            return result;
        }
    }

    public record Result(int blocks, Grade grade) {
    }
}
