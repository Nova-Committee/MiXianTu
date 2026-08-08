package com.iafenvoy.mxt.runtime.economy;

import com.iafenvoy.mxt.data.CurrencyValue;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.util.ItemMatcher;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.OptionalLong;

/**
 * Server-side read API for item-backed currency definitions.
 */
public final class CurrencyValueService {
    private CurrencyValueService() {
    }

    public static OptionalLong unitValue(Item item) {
        return ItemMatcher.find(MxtDatapackRegistries.holders(MxtDatapackRegistries.CURRENCY).map(Reference::value), new ItemStack(item))
                .map(CurrencyValue::value)
                .map(OptionalLong::of)
                .orElseGet(OptionalLong::empty);
    }

    /**
     * Reads one currency value from the client-synchronised datapack registries.
     */
    public static OptionalLong unitValue(Provider access, Item item) {
        return ItemMatcher.find(MxtDatapackRegistries.holders(access, MxtDatapackRegistries.CURRENCY).map(Reference::value), new ItemStack(item))
                .map(CurrencyValue::value)
                .map(OptionalLong::of)
                .orElseGet(OptionalLong::empty);
    }

    public static OptionalLong value(ItemStack stack) {
        if (stack.isEmpty()) return OptionalLong.of(0L);
        OptionalLong unit = unitValue(stack.getItem());
        if (unit.isEmpty() || unit.getAsLong() > Long.MAX_VALUE / stack.getCount()) return OptionalLong.empty();
        return OptionalLong.of(unit.getAsLong() * stack.getCount());
    }

    /**
     * Returns the selected exchange choices for one currency input item.
     */
    public static List<ExchangeOffer> exchangeOffers(ItemStack input) {
        if (input.isEmpty()) return List.of();
        return MxtDatapackRegistries.holders(MxtDatapackRegistries.CURRENCY)
                .map(Reference::value)
                .filter(definition -> definition.entries().stream().anyMatch(entry -> entry.matches(input)))
                .flatMap(definition -> definition.exchanges().stream())
                .map(exchange -> new ExchangeOffer(exchange.result().create(), exchange.cost()))
                .toList();
    }

    public static List<ExchangeOffer> exchangeOffers(RegistryAccess registryAccess, ItemStack input) {
        if (input.isEmpty()) return List.of();
        return MxtDatapackRegistries.holders(registryAccess, MxtDatapackRegistries.CURRENCY)
                .map(Reference::value)
                .filter(definition -> definition.entries().stream().anyMatch(entry -> entry.matches(input)))
                .flatMap(definition -> definition.exchanges().stream())
                .map(exchange -> new ExchangeOffer(exchange.result().create(), exchange.cost()))
                .toList();
    }

    /**
     * Checks whether a stack can occupy the exchange input slot, even before its count is sufficient.
     */
    public static boolean isExchangeInput(RegistryAccess registryAccess, ItemStack input) {
        if (input.isEmpty()) return false;
        return MxtDatapackRegistries.holders(registryAccess, MxtDatapackRegistries.CURRENCY)
                .map(Reference::value)
                .anyMatch(definition -> definition.entries().stream().anyMatch(entry -> entry.matches(input)) && !definition.exchanges().isEmpty());
    }

    /**
     * Returns empty when any stack is not configured as currency or the sum overflows.
     */
    public static OptionalLong totalValue(Collection<ItemStack> stacks) {
        long total = 0L;
        for (ItemStack stack : stacks) {
            OptionalLong value = value(stack);
            if (value.isEmpty() || value.getAsLong() > Long.MAX_VALUE - total) return OptionalLong.empty();
            total += value.getAsLong();
        }
        return OptionalLong.of(total);
    }

    //FIXME::Merge with CUrrencyValueDefinition.Exchange
    public record ExchangeOffer(ItemStack output, int cost) {
        public ExchangeOffer {
            output = output.copy();
        }

        @Override
        public ItemStack output() {
            return this.output;
        }
    }

}
