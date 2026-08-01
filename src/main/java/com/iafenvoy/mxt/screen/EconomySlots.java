package com.iafenvoy.mxt.screen;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.function.Predicate;

public final class EconomySlots {
    private EconomySlots() {
    }

    public static final class Input extends Slot {
        private final Predicate<ItemStack> predicate;

        public Input(Container container, int index, int x, int y, Predicate<ItemStack> predicate) {
            super(container, index, x, y);
            this.predicate = predicate;
        }

        @Override
        public boolean mayPlace(@NonNull ItemStack stack) {
            return this.predicate.test(stack);
        }
    }

    public static class Output extends Slot {
        public Output(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(@NonNull ItemStack stack) {
            return false;
        }
    }

    /**
     * A configuration slot: it stores a template copy and never consumes the cursor stack.
     */
    public static final class Ghost extends Slot {
        private final AbstractContainerMenu menu;

        public Ghost(AbstractContainerMenu menu, Container container, int index, int x, int y) {
            super(container, index, x, y);
            this.menu = menu;
        }

        @Override
        public boolean mayPickup(@NonNull Player player) {
            this.setByPlayer(ItemStack.EMPTY);
            this.menu.slotsChanged(this.container);
            return false;
        }

        @Override
        public @NonNull ItemStack safeInsert(ItemStack stack, int count) {
            this.setByPlayer(stack.copy());
            this.menu.slotsChanged(this.container);
            return stack;
        }
    }

    /**
     * A template visible to customers but unavailable for every inventory operation.
     */
    public static final class Display extends Output {
        public Display(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPickup(@NonNull Player player) {
            return false;
        }
    }
}
