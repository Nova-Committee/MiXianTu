package com.iafenvoy.mxt.item.block;

import com.iafenvoy.mxt.item.block.entity.SpiritHerbPlotBlockEntity;
import com.iafenvoy.mxt.registry.MxtBlockEntities;
import com.iafenvoy.mxt.runtime.alchemy.SpiritHerbGrowthService;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;

/**
 * A low soil bed. The plant above it is drawn by the block entity, so the collision matches only the bed.
 */
public final class SpiritHerbPlotBlock extends BaseEntityBlock {
    public static final double SOIL_HEIGHT = 4.0D / 16.0D;
    private static final MapCodec<SpiritHerbPlotBlock> CODEC = simpleCodec(SpiritHerbPlotBlock::new);
    private static final VoxelShape SHAPE = box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D);

    public SpiritHerbPlotBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @NonNull MapCodec<SpiritHerbPlotBlock> codec() {
        return CODEC;
    }

    @Override
    public @NonNull BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new SpiritHerbPlotBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NonNull Level level, @NonNull BlockState state, @NonNull BlockEntityType<T> type) {
        return createTickerHelper(type, MxtBlockEntities.SPIRIT_HERB_PLOT.get(), SpiritHerbPlotBlockEntity::serverTick);
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected @NonNull VoxelShape getCollisionShape(@NonNull BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected @NonNull VoxelShape getOcclusionShape(@NonNull BlockState state) {
        return Shapes.empty();
    }

    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected @NonNull InteractionResult useItemOn(@NonNull ItemStack stack, @NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos, @NonNull Player player, @NonNull InteractionHand hand, @NonNull BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SpiritHerbPlotBlockEntity plot)) return InteractionResult.PASS;
        // ServerPlayerGameMode only continues into useWithoutItem for this result. PASS stops the click.
        if (stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide())
            return SpiritHerbGrowthService.previewPlant(player, level, pos, stack, plot)
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        if (!(player instanceof ServerPlayer server) || !(level instanceof ServerLevel serverLevel))
            return InteractionResult.PASS;
        return SpiritHerbGrowthService.plant(server, serverLevel, pos, stack, plot);
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos, @NonNull Player player, @NonNull BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SpiritHerbPlotBlockEntity plot) || !plot.occupied())
            return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer server) || !(level instanceof ServerLevel serverLevel))
            return InteractionResult.PASS;
        return SpiritHerbGrowthService.emptyHand(server, serverLevel, pos, plot, player.isShiftKeyDown());
    }
}
