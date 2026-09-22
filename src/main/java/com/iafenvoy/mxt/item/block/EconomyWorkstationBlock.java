package com.iafenvoy.mxt.item.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;

/**
 * Shared base for the economy workstations. Menu behavior is supplied by the concrete workstation types.
 */
public abstract class EconomyWorkstationBlock extends Block {
    protected EconomyWorkstationBlock(Properties properties) {
        super(properties);
    }

    // Empty so the open-air counter top does not occlude its neighbours.
    @Override
    protected @NonNull VoxelShape getOcclusionShape(@NonNull BlockState state) {
        return Shapes.empty();
    }
}
