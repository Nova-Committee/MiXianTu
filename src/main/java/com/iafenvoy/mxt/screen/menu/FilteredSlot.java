package com.iafenvoy.mxt.screen.menu;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.function.Predicate;

/**
 * A slot that takes one kind of item. The station's paper slot accepts a whole stack on purpose: the default
 * cost takes one sheet, and a formula may ask for more through its own {@code mxt:item} costs, which are paid out
 * of this same slot - capping it at one sheet would make every such formula unaffordable.
 */
public class FilteredSlot extends Slot {
    private final Predicate<ItemStack> filter;

    public FilteredSlot(Container container, int index, int x, int y, Predicate<ItemStack> filter) {
        super(container, index, x, y);
        this.filter = filter;
    }

    @Override
    public boolean mayPlace(@NonNull ItemStack stack) {
        return this.filter.test(stack);
    }
}
