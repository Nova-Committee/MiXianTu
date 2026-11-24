package com.iafenvoy.mxt.screen.menu;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

final class OfferRevision {
    private static final AtomicLong NEXT_REVISION = new AtomicLong();
    final List<DataSlot> segments = List.of(DataSlot.standalone(), DataSlot.standalone(), DataSlot.standalone(), DataSlot.standalone());
    private List<ItemStack> first = List.of(), second = List.of();

    long value() {
        long revision = 0;
        for (int index = 0; index < this.segments.size(); index++)
            revision |= (long) (this.segments.get(index).get() & 0xffff) << (16 * index);
        return revision;
    }

    void refresh(Container first, Container second) {
        if (matches(first, this.first) && matches(second, this.second)) return;
        this.first = snapshot(first);
        this.second = snapshot(second);
        long revision = NEXT_REVISION.incrementAndGet();
        // Vanilla sends menu values as signed shorts, after the item slot updates.
        for (int index = 0; index < this.segments.size(); index++)
            this.segments.get(index).set((short) (revision >>> (16 * index)));
    }

    private static boolean matches(Container container, List<ItemStack> snapshot) {
        if (container.getContainerSize() != snapshot.size()) return false;
        for (int slot = 0; slot < snapshot.size(); slot++)
            if (!ItemStack.matches(container.getItem(slot), snapshot.get(slot))) return false;
        return true;
    }

    private static List<ItemStack> snapshot(Container container) {
        return IntStream.range(0, container.getContainerSize()).mapToObj(slot -> container.getItem(slot).copy()).toList();
    }
}
