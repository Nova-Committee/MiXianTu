package com.iafenvoy.mxt.screen.multiblock;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Cells are local coordinates with the controller at the origin and y allowed to be negative; the view reveals them
 * from the lowest layer up. A cell with a state draws as that block, one without draws its item model.
 */
public record MultiblockStructure(Component title, List<BlockEntry> blocks) {
    public MultiblockStructure {
        Objects.requireNonNull(title);
        blocks = List.copyOf(blocks);
    }

    public record BlockEntry(int x, int y, int z, ItemStack icon, @Nullable BlockState state) {
        public BlockEntry {
            Objects.requireNonNull(icon);
            icon = icon.copy();
        }

        public static BlockEntry of(int x, int y, int z, ItemLike item) {
            return new BlockEntry(x, y, z, new ItemStack(item), defaultState(item));
        }

        public static BlockEntry of(int x, int y, int z, BlockState state) {
            return new BlockEntry(x, y, z, new ItemStack(state.getBlock()), state);
        }

        // For a cell whose item model is the one worth showing: the block state would lose that look.
        public static BlockEntry asItem(int x, int y, int z, ItemStack icon) {
            return new BlockEntry(x, y, z, icon, null);
        }

        private static @Nullable BlockState defaultState(ItemLike item) {
            return item instanceof BlockItem blockItem ? blockItem.getBlock().defaultBlockState() : null;
        }
    }
}
