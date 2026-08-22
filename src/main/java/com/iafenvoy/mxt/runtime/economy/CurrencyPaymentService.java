package com.iafenvoy.mxt.runtime.economy;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.*;
import org.jetbrains.annotations.Nullable;
import java.util.OptionalLong;

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

    public static List<Denomination> denominations() {
        return MxtDatapackRegistries.holders(MxtResourceKeys.CURRENCY)
                .map(Reference::value)
                .flatMap(definition -> BuiltInRegistries.ITEM.stream()
                        .filter(item -> {
                            ItemStack stack = new ItemStack(item);
                            return definition.entries().stream().anyMatch(entry -> entry.matches(stack));
                        })
                        .map(item -> new Denomination(item, definition.value())))
                .distinct()
                .sorted(Comparator.comparingLong(Denomination::value).reversed())
                .toList();
    }

    public record Denomination(Item item, long value) {
    }
}
