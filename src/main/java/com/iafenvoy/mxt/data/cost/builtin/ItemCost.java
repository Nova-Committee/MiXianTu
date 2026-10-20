package com.iafenvoy.mxt.data.cost.builtin;

import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.CostTarget;
import com.iafenvoy.mxt.data.cost.CostTransaction;
import com.iafenvoy.mxt.data.cost.Debit;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.runtime.resulting.ResultWithData;
import com.iafenvoy.mxt.runtime.resulting.failures.CostFailure;
import com.iafenvoy.mxt.util.MiscUtil;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Takes matching items out of the payer's inventory, or out of a container the call site names. The store is any
 * {@link Container}, so an item cost can be paid out of a block entity's own slot (the talisman workstation's paper
 * slot) exactly like out of a player's inventory.
 * <p>
 * The count is read off the payer alone, not off the caller's variables, and is frozen when the entry is priced.
 */
public record ItemCost(List<Entry> entries, NumberProvider amount) implements ItemMatcher, Cost {
    public static final MapCodec<ItemCost> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ENTRIES_CODEC.fieldOf("items").forGetter(ItemMatcher::entries),
            NumberProvider.CODEC.fieldOf("amount").forGetter(ItemCost::amount)
    ).apply(i, ItemCost::new));

    @Override
    public ResultWithData<Transaction> prepare(CostContext context) {
        // The store is bound when the context offers one and refused when the fee is paid otherwise: a price can be read
        // without an inventory, and a context without one is a channel this entry does not have.
        Container store = store(context);
        double value = this.amount().evaluate(context.payer() == null ? FormulaContext.EMPTY
                : FormulaContext.of(context.payer()));
        if (count(value) <= 0) return ResultWithData.failure(null, CostFailure.INVALID_AMOUNT);
        return ResultWithData.success(new ItemTransaction(store, this.entries(), value));
    }

    @Override
    public MapCodec<ItemCost> codec() {
        return CODEC;
    }

    // Costs are matched, never ranked: the entries come from the record component, and the priority is the default.
    @Override
    public int priority() {
        return DEFAULT_PRIORITY;
    }

    // The store an item entry is paid out of here, or null when this context offers none.
    private static @Nullable Container store(CostContext context) {
        Container target = context.itemTarget();
        if (target != null) return target;
        Player player = context.player();
        return player == null ? null : player.getInventory();
    }

    // An amount of items is whole: the one place a count is rounded, so a replacement keeps the price it was given and
    // only the passes below read it. A price this container cannot express is refused (-1) rather than clamped - there
    // is no count less than the bill that may be taken and reported as the whole of it. Zero means "no amount".
    private static int count(double amount) {
        double value = MiscUtil.positive(amount);
        if (value <= 0.0D) return 0;
        return value > Integer.MAX_VALUE ? -1 : (int) Math.ceil(value);
    }

    /**
     * One count of items out of one container. The slots are planned read-only first and written only once the whole
     * plan is known to hold, so a refusal leaves the container exactly as it was and the debit names the stacks that
     * really left.
     */
    private static final class ItemTransaction extends CostTransaction {
        private final @Nullable Container store;
        private final List<ItemMatcher.Entry> entries;

        private ItemTransaction(@Nullable Container store, List<ItemMatcher.Entry> entries, double count) {
            super(new CostTarget.Items(store, entries), count);
            this.store = store;
            this.entries = entries;
        }

        @Override
        protected ResultWithData<List<Debit>> perform(boolean simulate) {
            if (this.store == null) return refused(CostFailure.NO_CHANNEL);
            int required = count(this.amount());
            if (required <= 0) return refused(CostFailure.INVALID_AMOUNT);
            List<Take> plan = this.plan(required);
            if (plan == null) return refused(CostFailure.MISSING_ITEM);
            if (simulate) return took(List.of());
            // Asked again right before the write: an entry priced earlier cannot take what another one already had.
            for (Take step : plan)
                if (this.store.getItem(step.slot()) != step.stack() || step.stack().getCount() < step.count())
                    return refused(CostFailure.MISSING_ITEM);
            List<ItemStack> taken = new ArrayList<>(plan.size());
            for (Take step : plan) {
                // Copied before the shrink: after it the slot may be empty and an empty stack no longer says what left.
                taken.add(step.stack().copyWithCount(step.count()));
                step.stack().shrink(step.count());
            }
            this.store.setChanged();
            return took(Debit.of(this.target(), required, taken));
        }

        @Override
        protected CostTransaction replacing(double amount) {
            return new ItemTransaction(this.store, this.entries, amount);
        }

        // An amount of items is whole: a scaled or merged count is rounded up once, here, and never written down.
        private static int count(double amount) {
            return ItemCost.count(amount);
        }

        // In slot order, the first matching stacks first: the order is the data contract, so a plan built twice from one
        // container is the same plan.
        private @Nullable List<Take> plan(int required) {
            List<Take> plan = new ArrayList<>();
            for (int slot = 0; slot < this.store.getContainerSize() && required > 0; slot++) {
                ItemStack stack = this.store.getItem(slot);
                if (stack.isEmpty() || !this.matches(stack)) continue;
                int used = Math.min(required, stack.getCount());
                plan.add(new Take(slot, stack, used));
                required -= used;
            }
            return required > 0 ? null : plan;
        }

        private boolean matches(ItemStack stack) {
            return this.entries.stream().anyMatch(entry -> entry.matches(stack));
        }
    }

    private record Take(int slot, ItemStack stack, int count) {
    }
}
