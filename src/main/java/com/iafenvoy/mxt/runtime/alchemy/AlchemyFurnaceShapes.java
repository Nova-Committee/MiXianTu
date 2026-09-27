package com.iafenvoy.mxt.runtime.alchemy;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// Generated from the Blockbench export by tools/export_alchemy_furnace.py; edit the model, not these bounds.
public final class AlchemyFurnaceShapes {
    private static final double[][] BOXES = {
            {3, 5, 3, 13, 11, 13},
            {2, 5, 4, 14, 10, 12},
            {4, 5, 2, 12, 10, 14},
            {4, 3, 4, 12, 4, 12},
            {3, 0, 3, 5, 4, 5},
            {11, 0, 3, 13, 4, 5},
            {7, 0, 11, 9, 4, 13},
            {3, 4, 3, 13, 5, 13},
            {2, 11, 2, 14, 12, 4},
            {2, 11, 12, 14, 12, 14},
            {2, 11, 4, 4, 12, 12},
            {12, 11, 4, 14, 12, 12},
            {4, 11, 4, 12, 12, 12},
            {3, 12, 3, 13, 13, 13},
            {5, 13, 5, 11, 14, 11},
            {0.5, 9, 5, 2, 14, 6.5},
            {0.5, 9, 9.5, 2, 14, 11},
            {0.5, 13, 6.5, 2, 14, 9.5},
            {14, 9, 5, 15.5, 14, 6.5},
            {14, 9, 9.5, 15.5, 14, 11},
            {14, 13, 6.5, 15.5, 14, 9.5},
            {7, 14, 7, 9, 15, 9},
            {6.5, 15, 6.5, 9.5, 16, 9.5},
            {5, 6, 1.99, 11, 9, 2.1},
            {5, 6, 13.9, 11, 9, 14.01},
            {1.99, 6, 5, 2.1, 9, 11},
            {13.9, 6, 5, 14.01, 9, 11}
    };
    private static final VoxelShape[][] PARTS = new VoxelShape[27][4];

    static {
        for (int rotation = 0; rotation < 4; rotation++) {
            for (int index = 0; index < 27; index++) PARTS[index][rotation] = build(index, rotation);
        }
    }

    private AlchemyFurnaceShapes() {
    }

    public static VoxelShape shape(int index, Direction facing) {
        return PARTS[index][rotation(facing)];
    }

    private static int rotation(Direction facing) {
        return switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    private static VoxelShape build(int index, int rotation) {
        int ox = index % 3 * 16;
        int oy = index / 9 * 16;
        int oz = index / 3 % 3 * 16;
        VoxelShape result = Shapes.empty();
        for (double[] box : BOXES) {
            double x0 = Math.max(0, box[0] * 3 - ox), y0 = Math.max(0, box[1] * 3 - oy), z0 = Math.max(0, box[2] * 3 - oz);
            double x1 = Math.min(16, box[3] * 3 - ox), y1 = Math.min(16, box[4] * 3 - oy), z1 = Math.min(16, box[5] * 3 - oz);
            if (x0 >= x1 || y0 >= y1 || z0 >= z1) continue;
            for (int turn = 0; turn < rotation; turn++) {
                double oldX0 = x0, oldX1 = x1;
                x0 = 16 - z1;
                x1 = 16 - z0;
                z0 = oldX0;
                z1 = oldX1;
            }
            result = Shapes.or(result, Shapes.box(x0 / 16, y0 / 16, z0 / 16, x1 / 16, y1 / 16, z1 / 16));
        }
        return result.optimize();
    }
}
