package com.iafenvoy.mxt.data.cost;

import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.iafenvoy.mxt.util.matcher.ItemMatcher.Entry;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Consumes matching items from the player's inventory.
 */
public record ItemCost(ItemMatcher matcher, NumberProvider amount) implements Cost {
    public static final MapCodec<ItemCost> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemMatcher.ENTRIES_CODEC.fieldOf("items").xmap(ItemCost::newMatcher, ItemCost::entries).forGetter(ItemCost::matcher),
            NumberProvider.CODEC.fieldOf("amount").forGetter(ItemCost::amount)
    ).apply(i, ItemCost::new));

    private static ItemMatcher newMatcher(List<Entry> entries) {
        return () -> entries;
    }

    private static List<Entry> entries(ItemMatcher matcher) {
        return matcher.entries();
    }

    @Override
    public boolean check(Player player) {
        int required = this.required(player);
        if (required <= 0) return false;
        int found = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (this.matcher.entries().stream().anyMatch(entry -> entry.matches(stack))) {
                found += stack.getCount();
                if (found >= required) return true;
            }
        }
        return false;
    }

    @Override
    public void consume(Player player) {
        int remaining = this.required(player);
        if (remaining <= 0) return;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (this.matcher.entries().stream().noneMatch(entry -> entry.matches(stack))) continue;
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
        player.getInventory().setChanged();
    }

    /**
     * Resolves the requested item count without changing the inventory.
     */
    public int required(Player player) {
        double value = this.amount.evaluate(FormulaContext.of(player));
        return Double.isFinite(value) && value > 0.0D ? (int) Math.ceil(value) : 0;
    }

    @Override
    public MapCodec<ItemCost> codec() {
        return CODEC;
    }
}
