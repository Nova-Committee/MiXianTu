package com.iafenvoy.mxt.runtime.economy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Currency-only operations shared by cheque and future station menus. No item is modified before a complete
 * plan has been validated.
 */
public final class CurrencyPaymentService {
    private CurrencyPaymentService() {
    }

    public static Optional<List<ItemStack>> makeChange(long value) {
        if (value < 0L) return Optional.empty();
        List<Denomination> denominations = denominations();
        List<ItemStack> result = new ArrayList<>();
        long remaining = value;
        for (Denomination denomination : denominations) {
            long count = remaining / denomination.value();
            remaining %= denomination.value();
            while (count > 0L) {
                int batch = (int) Math.min(count, denomination.item().getDefaultMaxStackSize());
                result.add(new ItemStack(denomination.item(), batch));
                count -= batch;
            }
        }
        return remaining == 0L ? Optional.of(result) : Optional.empty();
    }

    public static OptionalLong collectCurrency(Container container) {
        return collectCurrency(container, null);
    }

    public static OptionalLong collectCurrency(Container container, @Nullable Entity holder) {
        long total = 0L;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) continue;
            OptionalLong value = CurrencyValueService.value(holder, stack);
            if (value.isEmpty() || value.getAsLong() > Long.MAX_VALUE - total) return OptionalLong.empty();
            total += value.getAsLong();
        }
        return OptionalLong.of(total);
    }

    // One entry per item: an item two definitions both claim is worth what the winner says, exactly as
    // value() reads it. Listing a losing definition would let this table mint a value the same stack is not
    // valued at.
    public static List<Denomination> denominations() {
        return BuiltInRegistries.ITEM.stream()
                .map(item -> new Denomination(item, CurrencyValueService.unitValue(item).orElse(0L)))
                .filter(denomination -> denomination.value() > 0L)
                .sorted(Comparator.comparingLong(Denomination::value).reversed())
                .toList();
    }

    public record Denomination(Item item, long value) {
    }
}
