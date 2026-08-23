package com.iafenvoy.mxt.runtime.economy;

import com.iafenvoy.mxt.data.CurrencyValue.UnavailableWhen;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.data.CurrencyValue;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Collection;
import java.util.List;
import java.util.OptionalLong;
import java.util.Optional;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side read API for item-backed currency definitions.
 */
//FIXME::Refactor method reloads
public final class CurrencyValueService {
    private CurrencyValueService() {
    }

    public static OptionalLong unitValue(@NotNull Item item) {
        return unitValue(new ItemStack(item));
    }

    /**
     * Returns the value of a complete stack.
     */
    public static OptionalLong unitValue(@NotNull ItemStack stack) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return OptionalLong.empty();
        return unitValue(server.registryAccess(), stack);
    }

    /**
     * Reads one currency value from the client-synchronised datapack registries.
     */
    public static OptionalLong unitValue(@NotNull Provider access, @NotNull Item item) {
        return unitValue(access, new ItemStack(item));
    }

    public static OptionalLong unitValue(@NotNull Provider access, @NotNull ItemStack stack) {
        if (stack.isEmpty()) return OptionalLong.of(0L);
        return find(access, stack).map(CurrencyValueService::unitValue).orElseGet(OptionalLong::empty);
    }

    public static OptionalLong unitValue(@NotNull Provider access, @Nullable Entity holder, @NotNull ItemStack stack, @NotNull FormulaContext context) {
        if (holder == null) return unitValue(access, stack);
        if (stack.isEmpty()) return OptionalLong.of(0L);
        return find(access, stack).map(definition -> unitValue(definition, holder, stack, context))
                .orElseGet(OptionalLong::empty);
    }

    public static OptionalLong unitValue(@NotNull RegistryAccess access, @Nullable Entity holder, @NotNull ItemStack stack) {
        if (holder == null) return unitValue(access, stack);
        if (stack.isEmpty()) return OptionalLong.of(0L);
        return find(access, stack).map(definition -> unitValue(definition, holder, stack, FormulaContext.EMPTY))
                .orElseGet(OptionalLong::empty);
    }

    private static OptionalLong unitValue(CurrencyValue definition) {
        return OptionalLong.of(isAvailable(definition) ? definition.value() : 0L);
    }

    private static OptionalLong unitValue(CurrencyValue definition, Entity holder, ItemStack stack, FormulaContext context) {
        return OptionalLong.of(isAvailable(definition, holder, stack, context) ? definition.value() : 0L);
    }

    public static OptionalLong value(@NotNull ItemStack stack) {
        if (stack.isEmpty()) return OptionalLong.of(0L);
        OptionalLong unit = unitValue(stack);
        if (unit.isEmpty() || unit.getAsLong() > Long.MAX_VALUE / stack.getCount()) return OptionalLong.empty();
        return OptionalLong.of(unit.getAsLong() * stack.getCount());
    }

    public static OptionalLong value(@Nullable Entity holder, @NotNull ItemStack stack) {
        if (holder == null) return value(stack);
        if (stack.isEmpty()) return OptionalLong.of(0L);
        OptionalLong unit = unitValue(holder.level().registryAccess(), holder, stack);
        if (unit.isEmpty() || unit.getAsLong() > Long.MAX_VALUE / stack.getCount()) return OptionalLong.empty();
        return OptionalLong.of(unit.getAsLong() * stack.getCount());
    }

    /**
     * Returns the selected exchange choices for one currency input item.
     */
    public static List<ExchangeOffer> exchangeOffers(ItemStack input) {
        if (input.isEmpty()) return List.of();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? List.of() : exchangeOffers(server.registryAccess(), input);
    }

    public static List<ExchangeOffer> exchangeOffers(RegistryAccess registryAccess, ItemStack input) {
        if (input.isEmpty()) return List.of();
        return matching(registryAccess, input)
                .filter(CurrencyValueService::isAvailable)
                .flatMap(definition -> definition.exchanges().stream())
                .map(exchange -> new ExchangeOffer(exchange.result().create(), exchange.cost()))
                .toList();
    }

    public static List<ExchangeOffer> exchangeOffers(@NotNull RegistryAccess registryAccess, @Nullable Entity holder, @NotNull ItemStack input) {
        if (holder == null) return exchangeOffers(registryAccess, input);
        if (input.isEmpty()) return List.of();
        return matching(registryAccess, input)
                .filter(definition -> isAvailable(definition, holder, input, FormulaContext.EMPTY))
                .flatMap(definition -> definition.exchanges().stream())
                .map(exchange -> new ExchangeOffer(exchange.result().create(), exchange.cost()))
                .toList();
    }

    /**
     * Checks whether a stack can occupy the exchange input slot, even before its count is sufficient.
     */
    public static boolean isExchangeInput(RegistryAccess registryAccess, ItemStack input) {
        if (input.isEmpty()) return false;
        return matching(registryAccess, input).anyMatch(definition -> isAvailable(definition) && !definition.exchanges().isEmpty());
    }

    public static boolean isExchangeInput(@NotNull RegistryAccess registryAccess, @Nullable Entity holder, @NotNull ItemStack input) {
        if (holder == null) return isExchangeInput(registryAccess, input);
        if (input.isEmpty()) return false;
        return matching(registryAccess, input)
                .anyMatch(definition -> isAvailable(definition, holder, input, FormulaContext.EMPTY)
                        && !definition.exchanges().isEmpty());
    }

    /**
     * Finds a currency definition by stack matcher without evaluating unavailable_when.
     */
    public static Optional<CurrencyValue> definition(Provider access, ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        return ItemMatcher.find(MxtDatapackRegistries.holders(access, MxtResourceKeys.CURRENCY).map(Reference::value), stack);
    }

    public static Optional<UnavailableWhen> unavailableReason(@NotNull Provider access, @Nullable Entity holder, @NotNull ItemStack stack) {
        return definition(access, stack).flatMap(definition -> definition.unavailableWhen().stream()
                .filter(entry -> holder != null && entry.condition().test(holder, stack, FormulaContext.EMPTY)).findFirst());
    }

    private static boolean isAvailable(CurrencyValue definition, @NotNull Entity holder, @NotNull ItemStack stack, @NotNull FormulaContext context) {
        return definition.unavailableWhen().stream()
                .noneMatch(entry -> entry.condition().test(holder, stack, context));
    }

    private static boolean isAvailable(CurrencyValue definition) {
        return definition.unavailableWhen().isEmpty();
    }

    private static Stream<CurrencyValue> matching(RegistryAccess access, ItemStack stack) {
        return MxtDatapackRegistries.holders(access, MxtResourceKeys.CURRENCY)
                .map(Reference::value)
                .filter(definition -> definition.entries().stream().anyMatch(entry -> entry.matches(stack)));
    }

    private static Optional<CurrencyValue> find(Provider access, ItemStack stack) {
        return ItemMatcher.find(MxtDatapackRegistries.holders(access, MxtResourceKeys.CURRENCY)
                .map(Reference::value), stack);
    }

    private static Optional<CurrencyValue> find(RegistryAccess access, ItemStack stack) {
        return ItemMatcher.find(MxtDatapackRegistries.holders(access, MxtResourceKeys.CURRENCY)
                .map(Reference::value), stack);
    }

    /**
     * Returns empty when any stack is not configured as currency or the sum overflows.
     */
    public static OptionalLong totalValue(@NotNull Collection<ItemStack> stacks) {
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
