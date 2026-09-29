package com.iafenvoy.mxt.screen.menu;

import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.screen.menu.AlchemyFurnaceMenu.View;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

/**
 * The only furnace open path. Casing and unrelated blocks do not open a menu.
 */
public final class AlchemyFurnaceMenus {
    private AlchemyFurnaceMenus() {
    }

    public static void open(ServerPlayer player, BlockPos accessPos) {
        View view = viewOf(player.level().getBlockState(accessPos));
        if (view == null) return;
        player.openMenu(new Opener(accessPos, view));
    }

    @Nullable
    public static View viewOf(BlockState state) {
        if (state.is(MxtBlocks.ALCHEMY_FURNACE.get())) return View.MONITOR;
        if (state.is(MxtBlocks.ALCHEMY_MAIN_INPUT.get())) return View.MAIN;
        if (state.is(MxtBlocks.ALCHEMY_AUXILIARY_INPUT.get())) return View.AUXILIARY;
        if (state.is(MxtBlocks.ALCHEMY_OUTPUT.get())) return View.OUTPUT;
        return null;
    }

    private record Opener(BlockPos accessPos, View view) implements MenuProvider {
        @Override
        public @NonNull Component getDisplayName() {
            return Component.translatable(switch (this.view) {
                case MONITOR -> "screen.mxt.alchemy.monitor";
                case MAIN -> "screen.mxt.alchemy.main_input";
                case AUXILIARY -> "screen.mxt.alchemy.auxiliary_input";
                case OUTPUT -> "screen.mxt.alchemy.output";
            });
        }

        @Override
        public @Nullable AbstractContainerMenu createMenu(int containerId, @NonNull Inventory inventory, Player player) {
            if (AlchemyFurnaceMenu.physicalOwner(player.level(), this.accessPos, this.view) == null) return null;
            return new AlchemyFurnaceMenu(containerId, inventory, this.accessPos, this.view);
        }

        @Override
        public void writeClientSideData(@NonNull AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
            buffer.writeBlockPos(this.accessPos);
            buffer.writeEnum(this.view);
        }
    }
}
