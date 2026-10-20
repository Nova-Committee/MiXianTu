package com.iafenvoy.mxt.runtime.economy;

import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Making change out of the item denominations a pack declares.
 *
 * <p>The split is greedy: the largest denomination that fits, then the next. That is only correct for a set the greedy
 * answer always solves, which is why {@link #greedyFailure} exists and why a set that is not is reported at load time
 * rather than at a counter. Supporting an arbitrary set would mean a bounded combination search on the server thread for
 * every cheque, which is a worse trade than a contract a pack can be checked against.
 */
public final class CurrencyPaymentService {
    /**
     * The widest value the load-time check will table. A set that only breaks past this is reported as unchecked
     * rather than checked, because the table is one boolean per value.
     */
    public static final long MAX_CHECKED_VALUE = 4_000_000L;

    private CurrencyPaymentService() {
    }

    /**
     * The items one value is paid out in, or empty when this pack's denominations cannot make exactly that value. The
     * plan is bounded by {@code capacity} stacks: a value needing more is refused while counting, so a large cheque
     * against a small denomination cannot spend the server thread building a list that will not fit.
     */
    public static Optional<List<ItemStack>> makeChange(@NotNull HolderLookup.Provider provider, long value, int capacity) {
        if (value < 0L) return Optional.empty();
        List<Denomination> denominations = denominations(provider);
        long remaining = value;
        int stacks = 0;
        for (Denomination denomination : denominations) {
            long count = remaining / denomination.value();
            remaining %= denomination.value();
            if (count <= 0L) continue;
            int max = denomination.item().getDefaultMaxStackSize();
            stacks = (int) Math.min(Integer.MAX_VALUE, stacks + (count + max - 1L) / max);
            if (stacks > capacity) return Optional.empty();
        }
        if (remaining != 0L) return Optional.empty();
        List<ItemStack> result = new ArrayList<>(stacks);
        remaining = value;
        for (Denomination denomination : denominations) {
            long count = remaining / denomination.value();
            remaining %= denomination.value();
            while (count > 0L) {
                int batch = (int) Math.min(count, denomination.item().getDefaultMaxStackSize());
                result.add(new ItemStack(denomination.item(), batch));
                count -= batch;
            }
        }
        return Optional.of(result);
    }

    public static OptionalLong collectCurrency(Container container, @NotNull Entity holder) {
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
    public static List<Denomination> denominations(@NotNull HolderLookup.Provider provider) {
        return BuiltInRegistries.ITEM.stream()
                .map(item -> new Denomination(item, CurrencyValueService.unitValue(provider, item).orElse(0L)))
                .filter(denomination -> denomination.value() > 0L)
                .sorted(Comparator.comparingLong(Denomination::value).reversed())
                .toList();
    }

    /**
     * The first value up to {@code limit} that any combination of {@code values} can pay but the greedy answer cannot,
     * or empty when greedy always answers whenever an answer exists.
     * <p>
     * This is the check a denomination set has to pass to be paid out greedily, and the number it reports is the one an
     * author needs. A value nothing can pay is not a disagreement: the counter refuses it either way.
     */
    public static OptionalLong greedyFailure(List<Long> values, long limit) {
        List<Long> ordered = values.stream().filter(value -> value > 0L).distinct()
                .sorted(Comparator.comparingLong(Long::longValue).reversed()).toList();
        int reachable = (int) Math.min(Math.max(0L, limit), MAX_CHECKED_VALUE);
        boolean[] exact = new boolean[reachable + 1];
        exact[0] = true;
        for (int value = 1; value <= reachable; value++)
            for (long denomination : ordered)
                if (denomination <= value && exact[(int) (value - denomination)]) {
                    exact[value] = true;
                    break;
                }
        for (int value = 1; value <= reachable; value++) {
            if (!exact[value]) continue;
            long remaining = value;
            for (long denomination : ordered) remaining %= denomination;
            if (remaining != 0L) return OptionalLong.of(value);
        }
        return OptionalLong.empty();
    }

    /**
     * The value a load-time check reads a set to: the sum of its two largest denominations. A set whose greedy answer
     * is wrong at any value is already wrong at one no larger than this, which is what makes the exhaustive table above
     * affordable instead of unbounded.
     */
    public static long checkBound(List<Long> values) {
        List<Long> ordered = values.stream().filter(value -> value > 0L).distinct()
                .sorted(Comparator.comparingLong(Long::longValue).reversed()).toList();
        if (ordered.isEmpty()) return 0L;
        long largest = ordered.get(0);
        long second = ordered.size() > 1 ? ordered.get(1) : 0L;
        return Math.min(MAX_CHECKED_VALUE, largest + second);
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
}
