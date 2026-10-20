package com.iafenvoy.mxt.runtime.economy;

import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.profiling.ProfilerScope;
import com.iafenvoy.mxt.util.profiling.ServerProfiling;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class CurrencyPaymentService {
    // Limits the normalized load-time diagnostic, never an actual payout.
    public static final long MAX_CHECKED_VALUE = 4_000_000L;

    private CurrencyPaymentService() {
    }

    // Counts and capacity are resolved before allocating any output stacks.
    public static Optional<List<ItemStack>> makeChange(@NotNull HolderLookup.Provider provider, long value, int capacity) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.ECONOMY_CHANGE)) {
            List<Denomination> denominations = denominations(provider);
            return planCounts(denominations, value, capacity).map(counts -> stacks(denominations, counts));
        }
    }

    // Counts follow the supplied denomination order; any exact plan within capacity is sufficient.
    public static Optional<long[]> planCounts(List<Denomination> denominations, long value, int capacity) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.ECONOMY_CHANGE)) {
            if (value < 0L || capacity < 0) return Optional.empty();
            if (value == 0L) return Optional.of(new long[denominations.size()]);
            for (Denomination denomination : denominations)
                if (denomination.value() <= 0L || denomination.item().getDefaultMaxStackSize() <= 0)
                    return Optional.empty();
            long[] greedy = greedy(denominations, value, capacity);
            if (greedy != null) return Optional.of(greedy);
            long[] searched = search(denominations, value, capacity);
            return Optional.ofNullable(searched);
        }
    }

    // The largest denomination that fits, then the next: null when a remainder is left or the stack budget runs out.
    private static long @Nullable [] greedy(List<Denomination> denominations, long value, int capacity) {
        long[] counts = new long[denominations.size()];
        long remaining = value;
        long stacks = 0L;
        for (int index = 0; index < denominations.size(); index++) {
            Denomination denomination = denominations.get(index);
            long count = remaining / denomination.value();
            remaining %= denomination.value();
            if (count <= 0L) continue;
            stacks += Math.ceilDiv(count, denomination.item().getDefaultMaxStackSize());
            if (stacks > capacity) return null;
            counts[index] = count;
        }
        return remaining == 0L ? counts : null;
    }

    private static long @Nullable [] search(List<Denomination> denominations, long value, int capacity) {
        int size = denominations.size();
        long[] divisors = new long[size + 1];
        long[] stackValues = new long[size + 1];
        for (int index = size - 1; index >= 0; index--) {
            Denomination denomination = denominations.get(index);
            divisors[index] = gcd(divisors[index + 1], denomination.value());
            stackValues[index] = Math.max(stackValues[index + 1],
                    saturatedMultiply(denomination.value(), denomination.item().getDefaultMaxStackSize()));
        }
        if (!canCover(value, capacity, divisors[0], stackValues[0])) return null;
        Map<Long, Plan> reachable = Map.of(value, new Plan(0, -1, 0L, null));
        for (int index = 0; index < size; index++) {
            Denomination denomination = denominations.get(index);
            int perStack = denomination.item().getDefaultMaxStackSize();
            Map<Long, Plan> next = new HashMap<>();
            for (Map.Entry<Long, Plan> entry : reachable.entrySet()) {
                long left = entry.getKey();
                Plan previous = entry.getValue();
                int available = capacity - previous.stacks();
                long max = Math.min(left / denomination.value(), (long) available * perStack);
                if (index == size - 1) {
                    if (left % denomination.value() == 0L && left / denomination.value() <= max)
                        return counts(new Plan(previous.stacks() + (int) Math.ceilDiv(max, perStack), index, max, previous), size);
                    continue;
                }
                for (long count = max; count >= 0L; count--) {
                    long remaining = left - count * denomination.value();
                    int stacks = previous.stacks() + (int) Math.ceilDiv(count, perStack);
                    if (remaining == 0L) return counts(new Plan(stacks, index, count, previous), size);
                    if (!canCover(remaining, capacity - stacks, divisors[index + 1], stackValues[index + 1])) continue;
                    Plan known = next.get(remaining);
                    // At the same layer and remainder, fewer occupied slots leave every future choice available.
                    if (known == null || stacks < known.stacks())
                        next.put(remaining, count == 0L ? previous : new Plan(stacks, index, count, previous));
                }
            }
            if (next.isEmpty()) return null;
            reachable = next;
        }
        return null;
    }

    private static boolean canCover(long value, int capacity, long divisor, long stackValue) {
        return capacity > 0 && divisor > 0L && value % divisor == 0L && Math.ceilDiv(value, stackValue) <= capacity;
    }

    private static long[] counts(Plan plan, int size) {
        long[] counts = new long[size];
        for (Plan entry = plan; entry != null && entry.denomination() >= 0; entry = entry.previous())
            counts[entry.denomination()] = entry.count();
        return counts;
    }

    private static long saturatedMultiply(long value, long factor) {
        return value > Long.MAX_VALUE / factor ? Long.MAX_VALUE : value * factor;
    }

    private static long gcd(long first, long second) {
        while (second != 0L) {
            long remainder = first % second;
            first = second;
            second = remainder;
        }
        return first;
    }

    private static List<ItemStack> stacks(List<Denomination> denominations, long[] counts) {
        List<ItemStack> result = new ArrayList<>();
        for (int index = 0; index < counts.length; index++) {
            long count = counts[index];
            if (count <= 0L) continue;
            int perStack = denominations.get(index).item().getDefaultMaxStackSize();
            while (count > 0L) {
                int batch = (int) Math.min(count, perStack);
                result.add(new ItemStack(denominations.get(index).item(), batch));
                count -= batch;
            }
        }
        return result;
    }

    public static OptionalLong collectCurrency(Container container, @NotNull Entity holder) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.ECONOMY)) {
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
    }

    // One entry per item: an item two definitions both claim is worth what the winner says, exactly as
    // value() reads it. Listing a losing definition would let this table mint a value the same stack is not
    // valued at.
    public static List<Denomination> denominations(@NotNull HolderLookup.Provider provider) {
        return BuiltInRegistries.ITEM.stream()
                .map(item -> new Denomination(item, CurrencyValueService.unitValue(provider, item).orElse(0L)))
                .filter(denomination -> denomination.value() > 0L)
                .sorted(Comparator.comparingLong(Denomination::value).reversed())
                .toList();
    }

    // This diagnostic checks at most MAX_CHECKED_VALUE multiples of the denominations' common divisor.
    public static OptionalLong greedyFailure(List<Long> values, long limit) {
        List<Long> ordered = values.stream().filter(value -> value > 0L).distinct()
                .sorted(Comparator.comparingLong(Long::longValue).reversed()).toList();
        long divisor = ordered.stream().reduce(0L, CurrencyPaymentService::gcd);
        if (divisor == 0L) return OptionalLong.empty();
        long[] normalized = ordered.stream().mapToLong(value -> value / divisor).toArray();
        int reachable = (int) Math.min(Math.max(0L, limit) / divisor, MAX_CHECKED_VALUE);
        boolean[] exact = new boolean[reachable + 1];
        exact[0] = true;
        for (int value = 1; value <= reachable; value++)
            for (long denomination : normalized)
                if (denomination <= value && exact[(int) (value - denomination)]) {
                    exact[value] = true;
                    break;
                }
        for (int value = 1; value <= reachable; value++) {
            if (!exact[value]) continue;
            long remaining = value;
            for (long denomination : normalized) remaining %= denomination;
            if (remaining != 0L) return OptionalLong.of(value * divisor);
        }
        return OptionalLong.empty();
    }

    // A diagnostic interval, not a proof of greedy solvability outside it or within a particular slot budget.
    public static long checkBound(List<Long> values) {
        List<Long> ordered = values.stream().filter(value -> value > 0L).distinct()
                .sorted(Comparator.comparingLong(Long::longValue).reversed()).toList();
        if (ordered.isEmpty()) return 0L;
        long largest = ordered.get(0);
        long second = ordered.size() > 1 ? ordered.get(1) : 0L;
        long sum = largest > Long.MAX_VALUE - second ? Long.MAX_VALUE : largest + second;
        long divisor = ordered.stream().reduce(0L, CurrencyPaymentService::gcd);
        return Math.min(saturatedMultiply(MAX_CHECKED_VALUE, divisor), sum);
    }

    /**
     * Every value the pack declares, largest first: the set a load-time check reads, taken from the definitions
     * themselves because an item's stack cannot be built before the item components are bound.
     */
    public static List<Long> declaredValues(@NotNull HolderLookup.Provider provider) {
        return MxtDatapackRegistries.holders(provider, MxtResourceKeys.CURRENCY)
                .map(holder -> holder.value().value())
                .filter(value -> value > 0L)
                .distinct()
                .sorted(Comparator.comparingLong(Long::longValue).reversed())
                .toList();
    }

    public record Denomination(Item item, long value) {
    }

    private record Plan(int stacks, int denomination, long count, @Nullable Plan previous) {
    }
}
