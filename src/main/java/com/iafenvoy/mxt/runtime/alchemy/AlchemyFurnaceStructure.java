package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.item.block.AlchemyFurnaceBlock;
import com.iafenvoy.mxt.item.block.AlchemyFurnaceCasingBlock;
import com.iafenvoy.mxt.item.block.AlchemyFurnaceInventoryBlock;
import com.iafenvoy.mxt.item.block.entity.AlchemyFurnaceCasingBlockEntity;
import com.iafenvoy.mxt.item.block.entity.AlchemyFurnaceInventoryBlockEntity;
import com.iafenvoy.mxt.registry.MxtBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fixed 3x3x3: the bottom layer anchors its four corners only, and the two layers above it are filled - the cell
 * the shape used to leave hollow (13) is a wall now.
 * Local index is {@code x + 3z + 9y}; the controller is 10 and the heating block goes in the bottom centre (4).
 * Main is 14 and auxiliary is 12 so a player facing the north front has the main port on their left.
 * Facing rotates cells with the same quarter turns as {@code AlchemyFurnaceShapes}. Never force-loads a chunk.
 */
public final class AlchemyFurnaceStructure {
    public static final int SIZE = 3;
    public static final int CONTROLLER_INDEX = 10;
    public static final int MAIN_INDEX = AlchemyInventoryKind.MAIN.index();
    public static final int AUXILIARY_INDEX = AlchemyInventoryKind.AUXILIARY.index();
    public static final int OUTPUT_INDEX = AlchemyInventoryKind.OUTPUT.index();
    /**
     * The bottom layer's centre: the cell the heating block goes in, directly under the sealed chamber. One of the
     * five cells the structure never validates, so nothing claims it and a furnace may lose its heat mid-batch.
     */
    public static final int HEAT_INDEX = 4;
    private static final int[] WALLS = wallIndices();

    private AlchemyFurnaceStructure() {
    }

    public static int[] walls() {
        return WALLS.clone();
    }

    /**
     * Whether this cell is part of the structure at all. The bottom layer is only held by its four corners: the
     * base under the walls is drawn by the corner blocks themselves, so the five cells between them are the
     * builder's business and nothing here - requirement, claim or chunk loading - looks at them.
     */
    public static boolean required(int index) {
        return index / 9 > 0 || index % 3 != 1 && index / 3 % 3 != 1;
    }

    public static boolean wall(int index) {
        return required(index) && index != CONTROLLER_INDEX && AlchemyInventoryKind.ofIndex(index) == null;
    }

    public static BlockPos world(BlockPos controller, Direction facing, int index) {
        int x = index % 3;
        int z = index / 3 % 3;
        int y = index / 9;
        int rx = x - 1;
        int ry = y - 1;
        int rz = z;
        int turns = turns(facing);
        for (int turn = 0; turn < turns; turn++) {
            int nextX = -rz;
            rz = rx;
            rx = nextX;
        }
        return controller.offset(rx, ry, rz);
    }

    public static BlockPos origin(BlockPos controller, Direction facing) {
        return world(controller, facing, 0);
    }

    /**
     * The cell a heat source block has to stand in. Rotates with the shell like every other cell.
     */
    public static BlockPos heatSource(BlockPos controller, Direction facing) {
        return world(controller, facing, HEAT_INDEX);
    }

    public static Status inspect(Level level, BlockPos controller, Direction facing) {
        List<BlockPos> missing = new ArrayList<>();
        List<BlockPos> unloaded = new ArrayList<>();
        List<BlockPos> conflicts = new ArrayList<>();
        boolean complete = true;
        for (int index = 0; index < 27; index++) {
            if (!required(index)) continue;
            BlockPos pos = world(controller, facing, index);
            if (!level.isLoaded(pos)) {
                unloaded.add(pos.immutable());
                complete = false;
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (index == CONTROLLER_INDEX) {
                if (!state.is(MxtBlocks.ALCHEMY_FURNACE.get()) || state.getValue(AlchemyFurnaceBlock.FACING) != facing) {
                    missing.add(pos.immutable());
                    complete = false;
                }
                continue;
            }
            if (!expected(state, index)) {
                missing.add(pos.immutable());
                complete = false;
                continue;
            }
            AlchemyInventoryKind kind = AlchemyInventoryKind.ofIndex(index);
            if (kind != null) {
                if (!(level.getBlockEntity(pos) instanceof AlchemyFurnaceInventoryBlockEntity inventory) || inventory.kind() != kind) {
                    missing.add(pos.immutable());
                    complete = false;
                } else if (inventory.claimedByOther(controller)) {
                    conflicts.add(pos.immutable());
                    complete = false;
                }
                continue;
            }
            if (!(level.getBlockEntity(pos) instanceof AlchemyFurnaceCasingBlockEntity casing) || casing.material().isEmpty()) {
                missing.add(pos.immutable());
                complete = false;
            } else if (casing.claimedByOther(controller)) {
                conflicts.add(pos.immutable());
                complete = false;
            }
        }
        BlockState core = level.isLoaded(controller) ? level.getBlockState(controller) : null;
        boolean formed = core != null && core.is(MxtBlocks.ALCHEMY_FURNACE.get()) && core.getValue(AlchemyFurnaceBlock.FORMED);
        return new Status(formed, complete, List.copyOf(missing), List.copyOf(unloaded), List.copyOf(conflicts), origin(controller, facing), facing);
    }

    public static void form(ServerLevel level, BlockPos controller, Direction facing, boolean lit) {
        for (int index = 0; index < 27; index++) {
            if (!required(index) || index == CONTROLLER_INDEX) continue;
            BlockPos pos = world(controller, facing, index);
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof AlchemyFurnaceInventoryBlock && level.getBlockEntity(pos) instanceof AlchemyFurnaceInventoryBlockEntity inventory) {
                inventory.claim(controller);
                BlockState formed = state.setValue(AlchemyFurnaceInventoryBlock.FACING, facing)
                        .setValue(AlchemyFurnaceInventoryBlock.LIT, lit)
                        .setValue(AlchemyFurnaceInventoryBlock.FORMED, true);
                if (state != formed) level.setBlock(pos, formed, 3);
                continue;
            }
            if (!(level.getBlockEntity(pos) instanceof AlchemyFurnaceCasingBlockEntity casing)) continue;
            casing.claim(controller);
            if (!state.is(MxtBlocks.ALCHEMY_FURNACE_CASING.get())) continue;
            BlockState formed = state.setValue(AlchemyFurnaceCasingBlock.FACING, facing)
                    .setValue(AlchemyFurnaceCasingBlock.LIT, lit)
                    .setValue(AlchemyFurnaceCasingBlock.PART, index + 1);
            if (state != formed) level.setBlock(pos, formed, 3);
        }
        BlockState state = level.getBlockState(controller);
        if (state.is(MxtBlocks.ALCHEMY_FURNACE.get()) && !state.getValue(AlchemyFurnaceBlock.FORMED))
            level.setBlock(controller, state.setValue(AlchemyFurnaceBlock.FORMED, true), 3);
    }

    /**
     * Reverts parts this controller claimed. Does not drop them or clear their inventories.
     */
    public static void release(ServerLevel level, BlockPos controller, Direction facing) {
        for (int index = 0; index < 27; index++) {
            if (!required(index) || index == CONTROLLER_INDEX) continue;
            BlockPos pos = world(controller, facing, index);
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof AlchemyFurnaceInventoryBlock && level.getBlockEntity(pos) instanceof AlchemyFurnaceInventoryBlockEntity inventory
                    && inventory.claimedBy(controller)) {
                inventory.clearClaim();
                if (state.getValue(AlchemyFurnaceInventoryBlock.FORMED) || state.getValue(AlchemyFurnaceInventoryBlock.LIT))
                    level.setBlock(pos, state.setValue(AlchemyFurnaceInventoryBlock.FORMED, false).setValue(AlchemyFurnaceInventoryBlock.LIT, false), 3);
                continue;
            }
            if (!state.is(MxtBlocks.ALCHEMY_FURNACE_CASING.get())) continue;
            if (!(level.getBlockEntity(pos) instanceof AlchemyFurnaceCasingBlockEntity casing) || !casing.claimedBy(controller))
                continue;
            casing.clearClaim();
            if (state.getValue(AlchemyFurnaceCasingBlock.PART) != 0 || state.getValue(AlchemyFurnaceCasingBlock.LIT))
                level.setBlock(pos, state.setValue(AlchemyFurnaceCasingBlock.PART, 0).setValue(AlchemyFurnaceCasingBlock.LIT, false), 3);
        }
        if (!level.isLoaded(controller)) return;
        BlockState state = level.getBlockState(controller);
        if (state.is(MxtBlocks.ALCHEMY_FURNACE.get()) && state.getValue(AlchemyFurnaceBlock.FORMED))
            level.setBlock(controller, state.setValue(AlchemyFurnaceBlock.FORMED, false), 3);
    }

    public static void syncLit(ServerLevel level, BlockPos controller, Direction facing, boolean lit) {
        if (!level.isLoaded(controller) || !(level.getBlockState(controller).getBlock() instanceof AlchemyFurnaceBlock))
            return;
        for (int index = 0; index < 27; index++) {
            if (!required(index) || index == CONTROLLER_INDEX) continue;
            BlockPos pos = world(controller, facing, index);
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof AlchemyFurnaceInventoryBlock
                    && level.getBlockEntity(pos) instanceof AlchemyFurnaceInventoryBlockEntity inventory
                    && inventory.claimedBy(controller) && state.getValue(AlchemyFurnaceInventoryBlock.FORMED)
                    && state.getValue(AlchemyFurnaceInventoryBlock.LIT) != lit)
                level.setBlock(pos, state.setValue(AlchemyFurnaceInventoryBlock.LIT, lit), 3);
            else if (state.is(MxtBlocks.ALCHEMY_FURNACE_CASING.get())
                    && level.getBlockEntity(pos) instanceof AlchemyFurnaceCasingBlockEntity casing && casing.claimedBy(controller)
                    && state.getValue(AlchemyFurnaceCasingBlock.PART) != 0 && state.getValue(AlchemyFurnaceCasingBlock.LIT) != lit)
                level.setBlock(pos, state.setValue(AlchemyFurnaceCasingBlock.LIT, lit), 3);
        }
    }

    private static boolean expected(BlockState state, int index) {
        AlchemyInventoryKind kind = AlchemyInventoryKind.ofIndex(index);
        if (kind == AlchemyInventoryKind.MAIN) return state.is(MxtBlocks.ALCHEMY_MAIN_INPUT.get());
        if (kind == AlchemyInventoryKind.AUXILIARY) return state.is(MxtBlocks.ALCHEMY_AUXILIARY_INPUT.get());
        if (kind == AlchemyInventoryKind.OUTPUT) return state.is(MxtBlocks.ALCHEMY_OUTPUT.get());
        return state.is(MxtBlocks.ALCHEMY_FURNACE_CASING.get());
    }

    private static int[] wallIndices() {
        int[] ids = new int[27];
        int cursor = 0;
        for (int index = 0; index < 27; index++) if (wall(index)) ids[cursor++] = index;
        return Arrays.copyOf(ids, cursor);
    }

    private static int turns(Direction facing) {
        return switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    public record Status(boolean formed, boolean complete, List<BlockPos> missing, List<BlockPos> unloaded,
                         List<BlockPos> conflicts, BlockPos origin, Direction facing) {
    }
}
