package com.iafenvoy.mxt.data.cost;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A detached inventory reservation: which slot each item cost comes out of, counted without touching the
 * inventory. Committing writes the reserved counts back, so discarding a draft is the whole rollback.
 *
 * <p>The store is any {@link Container}, so an item cost can be paid out of a block entity's own slot (the
 * talisman workstation's paper slot) exactly like out of a player's inventory.
 */
public final class ItemCostDraft {
    private final Container container;
    private final Map<Integer, Integer> remaining = new LinkedHashMap<>();

    public ItemCostDraft(Container container) {
        this.container = container;
        for (int slot = 0; slot < container.getContainerSize(); slot++)
            this.remaining.put(slot, container.getItem(slot).getCount());
    }

    public ItemCostDraft(Player player) {
        this(player.getInventory());
    }

    public boolean reserve(Charge.Items charge) {
        int required = charge.count();
        if (required <= 0) return false;
        for (int slot = 0; slot < this.container.getContainerSize() && required > 0; slot++) {
            ItemStack stack = this.container.getItem(slot);
            if (charge.matcher().entries().stream().noneMatch(entry -> entry.matches(stack))) continue;
            int available = this.remaining.getOrDefault(slot, 0);
            int used = Math.min(required, available);
            this.remaining.put(slot, available - used);
            required -= used;
        }
        return required == 0;
    }

    public void commit() {
        boolean changed = false;
        for (Map.Entry<Integer, Integer> entry : this.remaining.entrySet()) {
            ItemStack stack = this.container.getItem(entry.getKey());
            if (stack.isEmpty() || stack.getCount() == entry.getValue()) continue;
            stack.setCount(entry.getValue());
            changed = true;
        }
        if (changed) this.container.setChanged();
    }
}
