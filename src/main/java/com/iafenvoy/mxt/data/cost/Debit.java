package com.iafenvoy.mxt.data.cost;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * What one fee really took: the store, the amount in that store's own unit, and - for items - the stacks that left it.
 * Only a real pass produces a debit: a simulated one takes nothing and answers with an empty list, and a refused fee
 * answers with the refusal instead.
 */
public record Debit(CostTarget target, double amount, List<ItemStack> items) {
    public Debit {
        items = List.copyOf(items);
    }

    public static Debit of(CostTarget target, double amount) {
        return new Debit(target, amount, List.of());
    }

    public static Debit of(CostTarget target, double amount, List<ItemStack> items) {
        return new Debit(target, amount, items);
    }
}
