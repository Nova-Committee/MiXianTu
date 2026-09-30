package com.iafenvoy.mxt.item.block;

import com.iafenvoy.mxt.item.block.entity.AlchemyFurnaceCasingBlockEntity;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyFurnaceShapes;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;


/**
 * One shell cell. Unformed it is a full cube. It does not open a furnace page. The stored wall item is the drop and the clone.
 */
public final class AlchemyFurnaceCasingBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 27);
    private static final MapCodec<AlchemyFurnaceCasingBlock> CODEC = simpleCodec(AlchemyFurnaceCasingBlock::new);

    public AlchemyFurnaceCasingBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false).setValue(PART, 0));
    }

    @Override
    protected @NonNull MapCodec<AlchemyFurnaceCasingBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, PART);
    }

    @Override
    public @NonNull BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new AlchemyFurnaceCasingBlockEntity(pos, state);
    }

    @Override
    public @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return shape(state);
    }

    @Override
    protected @NonNull VoxelShape getCollisionShape(@NonNull BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return shape(state);
    }

    private static VoxelShape shape(BlockState state) {
        int part = state.getValue(PART);
        return part == 0 ? Shapes.block() : AlchemyFurnaceShapes.shape(part - 1, state.getValue(FACING));
    }

    @Override
    public void setPlacedBy(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state, @Nullable LivingEntity placer, @NonNull ItemStack stack) {
        Direction facing = Direction.NORTH;
        if (placer != null && placer.getDirection().getAxis().isHorizontal())
            facing = placer.getDirection().getOpposite();
        level.setBlock(pos, state.setValue(FACING, facing), 3);
        if (level.getBlockEntity(pos) instanceof AlchemyFurnaceCasingBlockEntity casing)
            casing.acceptWallItem(stack.copyWithCount(1));
    }

    @Override
    public @NonNull ItemStack getCloneItemStack(@NonNull LevelReader level, @NonNull BlockPos pos, @NonNull BlockState state, boolean includeData, @NonNull Player player) {
        if (level.getBlockEntity(pos) instanceof AlchemyFurnaceCasingBlockEntity casing && !casing.wallItem().isEmpty())
            return casing.wallItem().copy();
        return new ItemStack(this);
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos, @NonNull Player player, @NonNull BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos, @NonNull RandomSource random) {
        if (level.getBlockEntity(pos) instanceof AlchemyFurnaceCasingBlockEntity casing) casing.validateClaim();
    }

}
