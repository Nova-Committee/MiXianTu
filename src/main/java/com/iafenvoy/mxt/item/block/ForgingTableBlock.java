package com.iafenvoy.mxt.item.block;

import com.iafenvoy.mxt.item.block.entity.ForgingTableBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;

/**
 * The forge table.
 *
 * <p>The block entity model is a 12/16 high desk built from the smithing table textures, so the
 * block is a slab rather than a full cube. Without the shape overrides the block would keep a
 * full-cube collision box, which lets players stand on an invisible layer above the model and
 * stops neighbouring faces from culling.</p>
 *
 * <p>The sound, map colour, strength and tool requirement are supplied by
 * {@code MxtBlocks#registerForging} rather than inherited: the vanilla smithing table is a wooden
 * block, which does not match an anvil-and-hammer workstation.</p>
 *
 * <p>The surface is a vanilla {@code AbstractContainerMenu}/{@code AbstractContainerScreen} pair,
 * so the block only has to open the block entity as a menu provider.</p>
 */
public final class ForgingTableBlock extends EconomyWorkstationBlock implements EntityBlock {
    /**
     * Matches the 12/16 height of {@code models/block/forging_table.json}.
     */
    private static final VoxelShape SHAPE = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.75D, 1.0D);
    private static final MapCodec<ForgingTableBlock> CODEC = simpleCodec(ForgingTableBlock::new);

    public ForgingTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @NonNull MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public @NonNull ForgingTableBlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new ForgingTableBlockEntity(pos, state);
    }

    @Override
    public @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected @NonNull VoxelShape getCollisionShape(@NonNull BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return SHAPE;
    }

    // getOcclusionShape is inherited from EconomyWorkstationBlock, which already returns
    // Shapes.empty() so the open-air counter top does not occlude its neighbours.

    @Override
    protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, Level level, @NonNull BlockPos pos, @NonNull Player player, @NonNull BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ForgingTableBlockEntity table)
            player.openMenu(table);
        return InteractionResult.SUCCESS;
    }
}
