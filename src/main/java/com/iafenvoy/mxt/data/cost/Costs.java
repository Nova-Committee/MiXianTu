package com.iafenvoy.mxt.data.cost;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.builtin.AuraCost;
import com.iafenvoy.mxt.data.cost.context.AuraCostContexts;
import com.iafenvoy.mxt.data.cost.context.AuraCostSources;
import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.saturn.data.cost.Debit;
import com.iafenvoy.saturn.data.cost.context.CostContext;
import com.iafenvoy.saturn.runtime.resulting.Result;
import com.mojang.serialization.DataResult;
import net.minecraft.core.Holder;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared aura-only validation and price projection. Repeated targets are diagnosed at execution time;
 * the payment keeps entries separate and ordered.
 */
public final class Costs {
    private Costs() {
    }

    /**
     * Load-time check for the fields that are aura costs by definition (a spirit crafting recipe's {@code aura},
     * {@code spirit_herb.costs}): a resource, item or script entry there could never be paid by the store the field
     * charges, so it is refused instead of quietly never crafting.
     */
    public static DataResult<List<Cost>> validateAuras(List<Cost> costs) {
        return costs.stream().allMatch(cost -> cost instanceof AuraCost)
                ? DataResult.success(costs)
                : DataResult.error(() -> "Every entry of an aura cost list must be an mxt:aura cost");
    }

    /**
     * The aura amounts the {@code mxt:aura} entries of a costs array would take, priced only - no store is asked.
     * Entries of another type are not this question and are left out, so one array can answer both.
     *
     * <p>Pricing is what answers the question, so a caller that also pays this array pays it twice: the cultivation
     * split needs the amounts before it can scale the bill. Null means an aura entry could not be priced.
     */
    public static @Nullable Map<Holder<Aura>, Double> auras(List<Cost> costs, CostContext context) {
        CostContext quote = AuraCostContexts.withAuraSource(context, AuraCostSources.QUOTE);
        Map<Holder<Aura>, Double> amounts = new LinkedHashMap<>();
        for (Cost cost : costs) {
            if (!(cost instanceof AuraCost)) continue;
            Result<Cost.Transaction> prepared = cost.prepare(quote);
            if (prepared instanceof Result.FailureResult<Cost.Transaction>) return null;
            Cost.Transaction transaction = prepared.getData().orElseThrow();
            if (transaction.target() instanceof AuraCostTarget target)
                amounts.merge(target.aura(), transaction.amount(), Double::sum);
        }
        return amounts;
    }

    /**
     * What a finished payment really took in aura, keyed by the aura itself. Only the known debits are reported, so a
     * store that handed over part of a fee shows that part and nothing more.
     */
    public static Map<Holder<Aura>, Double> auras(Result<List<Debit>> result) {
        Map<Holder<Aura>, Double> amounts = new LinkedHashMap<>();
        for (Debit debit : result.getData().orElse(List.of()))
            if (debit.target() instanceof AuraCostTarget target)
                amounts.merge(target.aura(), debit.amount(), Double::sum);
        return amounts;
    }
}
