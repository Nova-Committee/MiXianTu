package com.iafenvoy.mxt.item.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;

/** Shared base for the economy workstations. Menu behavior is supplied by the concrete workstation types. */
public abstract class EconomyWorkstationBlock extends Block {
    protected EconomyWorkstationBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @NonNull VoxelShape getOcclusionShape(@NonNull BlockState state) {
        return Shapes.empty();
    }
}
