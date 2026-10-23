package com.iafenvoy.mxt.data.cost;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.builtin.AuraCost;
import com.iafenvoy.mxt.data.cost.context.AuraCostSources;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.mojang.serialization.DataResult;
import net.minecraft.core.Holder;
import org.jetbrains.annotations.Nullable;

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
     * The aura amounts the {@code mxt:aura} entries of a costs array would take, priced only - no store is asked. For
     * the callers that pay a pool or a store by hand (a per-tick cultivation settlement that holds the chunk store
     * rather than a level), and for the shared-pool allocation, which needs the amounts before it can scale them.
     * Entries of another type are not this question and are left out, so one array can answer both.
     * <p>
     * Null means an aura entry could not be priced.
     */
    public static @Nullable Map<Holder<Aura>, Double> auras(List<Cost> costs, CostContext context) {
        List<Cost> auras = costs.stream().filter(cost -> cost instanceof AuraCost).toList();
        if (auras.isEmpty()) return Map.of();
        CostPayment payment = CostPayment.of(context.withAuraSource(AuraCostSources.QUOTE));
        if (payment.price(auras).isPresent()) return null;
        return payment.auras();
    }
}
