package com.iafenvoy.mxt.item.block;

import com.iafenvoy.mxt.item.block.entity.AlchemyFurnaceInventoryBlockEntity;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyFurnaceShapes;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyInventoryKind;
import com.iafenvoy.mxt.screen.menu.AlchemyFurnaceMenus;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;


/**
 * Main, auxiliary or output port. The part index is fixed by {@link AlchemyInventoryKind}; the block state only
 * carries facing, formed and lit. Unformed collision is a full cube.
 */
public final class AlchemyFurnaceInventoryBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = AlchemyFurnaceBlock.FACING;
    public static final BooleanProperty LIT = AlchemyFurnaceBlock.LIT;
    public static final BooleanProperty FORMED = AlchemyFurnaceBlock.FORMED;
    private final AlchemyInventoryKind kind;
    private final MapCodec<AlchemyFurnaceInventoryBlock> codec;

    public AlchemyFurnaceInventoryBlock(Properties properties, AlchemyInventoryKind kind) {
        super(properties);
        this.kind = kind;
        this.codec = simpleCodec(next -> new AlchemyFurnaceInventoryBlock(next, kind));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false).setValue(FORMED, false));
    }

    public AlchemyInventoryKind kind() {
        return this.kind;
    }

    @Override
    protected @NonNull MapCodec<AlchemyFurnaceInventoryBlock> codec() {
        return this.codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, FORMED);
    }

    @Override
    public @NonNull BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new AlchemyFurnaceInventoryBlockEntity(pos, state);
    }

    @Override
    public @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return this.shape(state);
    }

    @Override
    protected @NonNull VoxelShape getCollisionShape(@NonNull BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return this.shape(state);
    }

    private VoxelShape shape(BlockState state) {
        return state.getValue(FORMED) ? AlchemyFurnaceShapes.shape(this.kind.index(), state.getValue(FACING)) : Shapes.block();
    }

    @Override
    public void setPlacedBy(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state, @Nullable LivingEntity placer, @NonNull ItemStack stack) {
        Direction facing = Direction.NORTH;
        if (placer != null && placer.getDirection().getAxis().isHorizontal())
            facing = placer.getDirection().getOpposite();
        level.setBlock(pos, state.setValue(FACING, facing), 3);
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos, @NonNull Player player, @NonNull BlockHitResult hit) {
        if (player instanceof ServerPlayer server) AlchemyFurnaceMenus.open(server, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos, @NonNull RandomSource random) {
        if (level.getBlockEntity(pos) instanceof AlchemyFurnaceInventoryBlockEntity inventory)
            inventory.validateClaim();
    }

}
