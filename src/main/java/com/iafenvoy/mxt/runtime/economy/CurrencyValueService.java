package com.iafenvoy.mxt.runtime.economy;

import com.iafenvoy.mxt.data.DescribedEntry;
import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.economy.CurrencyValue;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.item.QualityService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Stream;

/**
 * Server-side read API for item-backed currency definitions.
 */
public final class CurrencyValueService {
    public static OptionalLong unitValue(@NotNull Provider provider, @NotNull Item item) {
        return unitValue(provider, new ItemStack(item));
    }

    public static OptionalLong unitValue(@NotNull Provider provider, @NotNull ItemStack stack) {
        if (stack.isEmpty()) return OptionalLong.of(0L);
        return find(provider, stack)
                .map(definition -> unitValue(provider, stack, definition, isAvailable(definition), FormulaContext.of(provider)))
                .orElseGet(OptionalLong::empty);
    }

    public static OptionalLong unitValue(@NotNull Provider provider, @Nullable Entity holder, @NotNull ItemStack stack, @NotNull FormulaContext context) {
        if (holder == null) return unitValue(provider, stack);
        if (stack.isEmpty()) return OptionalLong.of(0L);
        return find(provider, stack)
                .map(definition -> unitValue(provider, stack, definition, isAvailable(definition, holder, stack, context), context))
                .orElseGet(OptionalLong::empty);
    }

    public static OptionalLong unitValue(@NotNull RegistryAccess provider, @Nullable Entity holder, @NotNull ItemStack stack) {
        if (holder == null) return unitValue(provider, stack);
        if (stack.isEmpty()) return OptionalLong.of(0L);
        return find(provider, stack)
                .map(definition -> unitValue(provider, stack, definition, isAvailable(definition, holder, stack, FormulaContext.of(provider)), FormulaContext.of(provider)))
                .orElseGet(OptionalLong::empty);
    }

    // The one place an item's currency value meets its quality: the denomination is multiplied by the
    // value_multiplier of the quality the priced stack itself resolves, because that stack is this settlement's
    // own input. A product that would fall below one, reach past a long, or stop being finite leaves the
    // declared denomination alone instead of clamping, so an unusable modifier can only ever mean "no change".
    private static OptionalLong unitValue(Provider provider, ItemStack stack, CurrencyValue definition, boolean available, FormulaContext context) {
        if (!available) return OptionalLong.of(0L);
        double multiplier = QualityService.modifier(provider, stack, ItemQuality::valueMultiplier, context);
        if (multiplier == QualityService.DEFAULT_MODIFIER) return OptionalLong.of(definition.value());
        double scaled = definition.value() * multiplier;
        if (!Double.isFinite(scaled) || scaled < 1.0D || scaled >= Long.MAX_VALUE)
            return OptionalLong.of(definition.value());
        return OptionalLong.of(Math.round(scaled));
    }

    public static OptionalLong value(@NotNull Entity holder, @NotNull ItemStack stack) {
        if (stack.isEmpty()) return OptionalLong.of(0L);
        OptionalLong unit = unitValue(holder.level().registryAccess(), holder, stack);
        if (unit.isEmpty() || unit.getAsLong() > Long.MAX_VALUE / stack.getCount()) return OptionalLong.empty();
        return OptionalLong.of(unit.getAsLong() * stack.getCount());
    }

    public static List<CurrencyValue.Exchange> exchangeOffers(RegistryAccess access, ItemStack input) {
        if (input.isEmpty()) return List.of();
        return matching(access, input)
                .filter(CurrencyValueService::isAvailable)
                .flatMap(definition -> definition.exchanges().stream())
                .toList();
    }

    public static List<CurrencyValue.Exchange> exchangeOffers(@NotNull RegistryAccess access, @Nullable Entity holder, @NotNull ItemStack input) {
        if (holder == null) return exchangeOffers(access, input);
        if (input.isEmpty()) return List.of();
        return matching(access, input)
                .filter(definition -> isAvailable(definition, holder, input, FormulaContext.of(access)))
                .flatMap(definition -> definition.exchanges().stream())
                .toList();
    }

    // Whether a stack can occupy the exchange input slot, even before its count is sufficient.
    public static boolean isExchangeInput(RegistryAccess access, ItemStack input) {
        if (input.isEmpty()) return false;
        return matching(access, input).anyMatch(definition -> isAvailable(definition) && !definition.exchanges().isEmpty());
    }

    public static boolean isExchangeInput(@NotNull RegistryAccess access, @Nullable Entity holder, @NotNull ItemStack input) {
        if (holder == null) return isExchangeInput(access, input);
        if (input.isEmpty()) return false;
        return matching(access, input)
                .anyMatch(definition -> isAvailable(definition, holder, input, FormulaContext.of(access))
                        && !definition.exchanges().isEmpty());
    }

    // Finds a currency definition by stack matcher without evaluating unavailable_when.
    public static Optional<CurrencyValue> definition(Provider provider, ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        return ItemMatcher.find(MxtDatapackRegistries.holders(provider, MxtResourceKeys.CURRENCY).map(Reference::value), stack);
    }

    public static Optional<DescribedEntry<ItemCondition>> unavailableReason(@NotNull Provider provider, @Nullable Entity holder, @NotNull ItemStack stack) {
        return definition(provider, stack).flatMap(definition -> definition.unavailableWhen().stream()
                .filter(entry -> holder != null && entry.value().test(holder, stack, FormulaContext.of(provider))).findFirst());
    }

    private static boolean isAvailable(CurrencyValue definition, @NotNull Entity holder, @NotNull ItemStack stack, @NotNull FormulaContext context) {
        return definition.unavailableWhen().stream()
                .noneMatch(entry -> entry.value().test(holder, stack, context));
    }

    private static boolean isAvailable(CurrencyValue definition) {
        return definition.unavailableWhen().isEmpty();
    }

    // The winner only: a stack's exchange quotes come from the same definition its value does, so a losing
    // declaration contributes neither a value nor exchanges.
    private static Stream<CurrencyValue> matching(RegistryAccess access, ItemStack stack) {
        return find(access, stack).stream();
    }

    private static Optional<CurrencyValue> find(Provider access, ItemStack stack) {
        return ItemMatcher.find(MxtDatapackRegistries.holders(access, MxtResourceKeys.CURRENCY).map(Reference::value), stack);
    }

    private static Optional<CurrencyValue> find(RegistryAccess access, ItemStack stack) {
        return ItemMatcher.find(MxtDatapackRegistries.holders(access, MxtResourceKeys.CURRENCY).map(Reference::value), stack);
    }
}
