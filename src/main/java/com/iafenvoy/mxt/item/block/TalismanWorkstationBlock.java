package com.iafenvoy.mxt.item.block;

import com.iafenvoy.mxt.screen.menu.TalismanWorkstationMenu;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

/**
 * The talisman drawing workstation. The collision shape is a slab matching the model's height: a full-cube box would
 * let players stand on an invisible layer above the model and would stop neighbouring faces from culling.
 *
 * <p>There is no block entity: the station stores nothing. The screen's one slot belongs to the menu, so closing it
 * hands its contents back, exactly like a crafting table.
 */
public final class TalismanWorkstationBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    // Matches the 12/16 height of models/block/talisman_workstation.json.
    private static final VoxelShape SHAPE = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.75D, 1.0D);
    private static final MapCodec<TalismanWorkstationBlock> CODEC = simpleCodec(TalismanWorkstationBlock::new);

    public TalismanWorkstationBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected @NonNull MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(@NonNull BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected @NonNull VoxelShape getCollisionShape(@NonNull BlockState state, @NonNull BlockGetter level, @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return SHAPE;
    }

    // The table top is open air above 12/16, so it must not occlude its neighbours.
    @Override
    protected @NonNull VoxelShape getOcclusionShape(@NonNull BlockState state) {
        return Shapes.empty();
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, Level level, @NonNull BlockPos pos, @NonNull Player player, @NonNull BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.openMenu(new MenuProvider() {
                @Override
                public @NonNull Component getDisplayName() {
                    return Component.translatable("block.mxt.talisman_workstation");
                }

                @Override
                public AbstractContainerMenu createMenu(int containerId, @NonNull Inventory inventory, @NonNull Player openedBy) {
                    return new TalismanWorkstationMenu(containerId, inventory, ContainerLevelAccess.create(level, pos));
                }
            });
        }
        return InteractionResult.SUCCESS;
    }
}
