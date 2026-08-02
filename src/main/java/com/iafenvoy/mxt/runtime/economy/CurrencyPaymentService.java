package com.iafenvoy.mxt.runtime.economy;

import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Currency-only operations shared by cheque and future station menus. No item
 * is modified before a complete plan has been validated.
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
        return remaining == 0L ? Optional.of(List.copyOf(result)) : Optional.empty();
    }

    public static OptionalLongResult collectCurrency(Container container) {
        long total = 0L;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) continue;
            OptionalLong value = CurrencyValueService.value(stack);
            if (value.isEmpty() || value.getAsLong() > Long.MAX_VALUE - total) return OptionalLongResult.invalid();
            total += value.getAsLong();
        }
        return OptionalLongResult.valid(total);
    }

    public static List<Denomination> denominations() {
        return MxtDatapackRegistries.holders(MxtDatapackRegistries.CURRENCY)
                .map(Reference::value)
                .flatMap(definition -> BuiltInRegistries.ITEM.stream()
                        .filter(item -> definition.entries().stream().anyMatch(entry -> entry.matches(new ItemStack(item))))
                        .map(item -> new Denomination(item, definition.value())))
                .distinct()
                .sorted(Comparator.comparingLong(Denomination::value).reversed())
                .toList();
    }

    public record Denomination(Item item, long value) {
    }

    public record OptionalLongResult(boolean valid, long value) {
        private static OptionalLongResult invalid() {
            return new OptionalLongResult(false, 0L);
        }

        private static OptionalLongResult valid(long value) {
            return new OptionalLongResult(true, value);
        }
    }
}
