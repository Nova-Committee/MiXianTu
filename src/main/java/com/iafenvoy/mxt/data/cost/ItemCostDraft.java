package com.iafenvoy.mxt.data.cost;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A detached inventory reservation: which slot each item cost comes out of, counted without touching the
 * inventory. Committing writes the reserved counts back, so discarding a draft is the whole rollback.
 */
public final class ItemCostDraft {
    private final Player player;
    private final Map<Integer, Integer> remaining = new LinkedHashMap<>();

    public ItemCostDraft(Player player) {
        this.player = player;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
            this.remaining.put(slot, player.getInventory().getItem(slot).getCount());
    }

    public boolean reserve(Charge.Items charge) {
        int required = charge.count();
        if (required <= 0) return false;
        for (int slot = 0; slot < this.player.getInventory().getContainerSize() && required > 0; slot++) {
            ItemStack stack = this.player.getInventory().getItem(slot);
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
            ItemStack stack = this.player.getInventory().getItem(entry.getKey());
            if (stack.isEmpty() || stack.getCount() == entry.getValue()) continue;
            stack.setCount(entry.getValue());
            changed = true;
        }
        if (changed) this.player.getInventory().setChanged();
    }
}
