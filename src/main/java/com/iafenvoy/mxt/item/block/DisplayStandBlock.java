package com.iafenvoy.mxt.item.block;

import com.iafenvoy.mxt.item.block.entity.DisplayStandBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;

/**
 * A one-slot wooden stand for displaying any item in the world.
 */
public final class DisplayStandBlock extends BaseEntityBlock {
    private static final MapCodec<DisplayStandBlock> CODEC = simpleCodec(DisplayStandBlock::new);
    private static final VoxelShape SHAPE = box(2.0D, 0.0D, 2.0D, 14.0D, 23.0D, 14.0D);

    public DisplayStandBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @NonNull MapCodec<DisplayStandBlock> codec() {
        return CODEC;
    }

    @Override
    public @NonNull BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new DisplayStandBlockEntity(pos, state);
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos,
                                           @NonNull CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected @NonNull VoxelShape getCollisionShape(@NonNull BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos,
                                                    @NonNull CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected @NonNull InteractionResult useItemOn(@NonNull ItemStack stack, @NonNull BlockState state, @NonNull Level level,
                                                   @NonNull BlockPos pos, @NonNull Player player, @NonNull InteractionHand hand,
                                                   @NonNull BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DisplayStandBlockEntity stand)) return InteractionResult.PASS;
        if (!stand.displayedItem().isEmpty()) {
            if (!level.isClientSide()) dropDisplayedItem(level, pos, stand.removeDisplayedItem());
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) {
            stand.setDisplayedItem(stack.copyWithCount(1));
            if (!player.hasInfiniteMaterials()) stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                                                        @NonNull Player player, @NonNull BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DisplayStandBlockEntity stand) || stand.displayedItem().isEmpty())
            return InteractionResult.PASS;
        if (!level.isClientSide()) dropDisplayedItem(level, pos, stand.removeDisplayedItem());
        return InteractionResult.SUCCESS;
    }

    @Override
    public @NonNull BlockState playerWillDestroy(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state, @NonNull Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof DisplayStandBlockEntity stand) {
            ItemStack displayed = stand.removeDisplayedItem();
            if (!displayed.isEmpty()) dropDisplayedItem(level, pos, displayed);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.MODEL;
    }

    private static void dropDisplayedItem(Level level, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty()) return;
        level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 1.5D, pos.getZ() + 0.5D, stack));
    }
}
