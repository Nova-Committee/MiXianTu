package com.iafenvoy.mxt.item.block;

import com.iafenvoy.mxt.screen.menu.ChequeTableMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;

/**
 * Workstation for converting configured currency items to and from cheques.
 */
public final class ChequeTableBlock extends EconomyWorkstationBlock {
    public ChequeTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, Level level, @NonNull BlockPos pos, @NonNull Player player, @NonNull BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.openMenu(new MenuProvider() {
                @Override
                public @NonNull Component getDisplayName() {
                    return Component.translatable("screen.mxt.cheque_table");
                }

                @Override
                public AbstractContainerMenu createMenu(int containerId, @NonNull Inventory inventory, @NonNull Player openedBy) {
                    return new ChequeTableMenu(containerId, inventory, ContainerLevelAccess.create(level, pos));
                }
            });
        }
        return InteractionResult.SUCCESS;
    }
}
