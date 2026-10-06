package com.iafenvoy.mxt.data.cost;

import com.iafenvoy.mxt.data.cost.builtin.ItemCost;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostFailure;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The inventory's draft: which slot each item entry comes out of, counted without touching the inventory. Its
 * constructor copies the counts the price is checked against, so a draft created on demand is cheap; committing
 * counts the reservation again from the counts the inventory holds at that moment and writes it, and {@link #reset}
 * puts back the counts of the slots that write touched, and nothing else.
 *
 * <p>The store is any {@link Container}, so an item cost can be paid out of a block entity's own slot (the talisman
 * workstation's paper slot) exactly like out of a player's inventory.
 */
public final class ItemCostDraft implements CostDraft<ItemCost> {
    private final @Nullable Container container;
    private final FormulaContext formula;
    private final Map<Integer, Integer> snapshot = new LinkedHashMap<>();
    private final Map<Integer, Integer> written = new LinkedHashMap<>();
    private final List<ItemCost> loaded = new ArrayList<>();

    public ItemCostDraft(CostContext context) {
        this.container = store(context);
        // An entry's amount is read the way it always was: off the payer alone, not off the caller's variables.
        this.formula = context.payer() == null ? FormulaContext.EMPTY : FormulaContext.of(context.payer());
        if (this.container == null) return;
        for (int slot = 0; slot < this.container.getContainerSize(); slot++)
            this.snapshot.put(slot, this.container.getItem(slot).getCount());
    }

    // The store an item entry is paid out of here, or null when this context offers none.
    private static @Nullable Container store(CostContext context) {
        Container target = context.itemTarget();
        if (target != null) return target;
        Player player = context.player();
        return player == null ? null : player.getInventory();
    }

    @Override
    public Optional<CostFailure> load(ItemCost cost) {
        if (this.container == null) return Optional.of(CostFailure.NO_CHANNEL);
        if (this.required(cost) <= 0) return Optional.of(CostFailure.INVALID_AMOUNT);
        this.loaded.add(cost);
        return Optional.empty();
    }

    @Override
    public Optional<CostFailure> test() {
        if (this.loaded.isEmpty()) return Optional.empty();
        return this.reservation(this.snapshot) == null ? Optional.of(CostFailure.MISSING_ITEM) : Optional.empty();
    }

    @Override
    public Optional<CostFailure> commit() {
        if (this.loaded.isEmpty()) return Optional.empty();
        // Reserved against the counts the inventory holds now, not the ones this draft was made with: two prices
        // written in one act each take their items, and the second must be measured against what the first took.
        // It is the caller's boundary that puts the first one back when the second cannot.
        Map<Integer, Integer> left = this.reservation(this.counts());
        if (left == null) return Optional.of(CostFailure.MISSING_ITEM);
        this.written.clear();
        this.apply(left);
        return Optional.empty();
    }

    @Override
    public void reset() {
        this.restore();
    }

    @Override
    public boolean chargesAmounts() {
        return false;
    }

    // What is left in the inventory after every loaded entry, or null when one of them does not fit.
    private @Nullable Map<Integer, Integer> reservation(Map<Integer, Integer> from) {
        if (this.container == null) return null;
        Map<Integer, Integer> left = new LinkedHashMap<>(from);
        for (ItemCost cost : this.loaded) {
            int required = this.required(cost);
            for (int slot = 0; slot < this.container.getContainerSize() && required > 0; slot++) {
                ItemStack stack = this.container.getItem(slot);
                if (stack.isEmpty() || cost.entries().stream().noneMatch(entry -> entry.matches(stack))) continue;
                int available = left.getOrDefault(slot, 0);
                int used = Math.min(required, available);
                left.put(slot, available - used);
                required -= used;
            }
            if (required > 0) return null;
        }
        return left;
    }

    private Map<Integer, Integer> counts() {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        if (this.container == null) return counts;
        for (int slot = 0; slot < this.container.getContainerSize(); slot++)
            counts.put(slot, this.container.getItem(slot).getCount());
        return counts;
    }

    // Writes the counts each slot is left with, remembering what every slot it changes held before: that is the
    // whole of what reset() has to put back.
    private void apply(Map<Integer, Integer> counts) {
        if (this.container == null) return;
        boolean changed = false;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            if (entry.getKey() >= this.container.getContainerSize()) continue;
            ItemStack stack = this.container.getItem(entry.getKey());
            if (stack.isEmpty() || stack.getCount() == entry.getValue()) continue;
            this.written.put(entry.getKey(), stack.getCount());
            stack.setCount(entry.getValue());
            changed = true;
        }
        if (changed) this.container.setChanged();
    }

    // Puts those counts back, and forgets them.
    private void restore() {
        if (this.container == null || this.written.isEmpty()) return;
        boolean changed = false;
        for (Map.Entry<Integer, Integer> entry : this.written.entrySet()) {
            if (entry.getKey() >= this.container.getContainerSize()) continue;
            ItemStack stack = this.container.getItem(entry.getKey());
            if (stack.isEmpty() || stack.getCount() == entry.getValue()) continue;
            stack.setCount(entry.getValue());
            changed = true;
        }
        this.written.clear();
        if (changed) this.container.setChanged();
    }

    private int required(ItemCost cost) {
        double value = cost.amount().evaluate(this.formula);
        return Double.isFinite(value) && value > 0.0D ? (int) Math.ceil(value) : 0;
    }
}
