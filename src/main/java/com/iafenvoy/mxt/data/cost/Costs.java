package com.iafenvoy.mxt.data.cost;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.builtin.AuraCost;
import com.iafenvoy.mxt.data.cost.builtin.ResourceCost;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostFailure;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.DataResult;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Load-time validation shared by every costs array.
 */
public final class Costs {
    private Costs() {
    }

    /**
     * Two entries that name the same store are rejected: the order they are written in is invisible at runtime, so
     * a duplicate can only be a mistake. Entries that reach the same resource through different routes (an aura and
     * the resource it is counted in) are not duplicates - the transaction adds them up, which is the one answer
     * that does not depend on order.
     */
    public static DataResult<List<Cost>> validate(List<Cost> costs) {
        Set<Identifier> resources = new HashSet<>();
        Set<Identifier> auras = new HashSet<>();
        for (Cost cost : costs) {
            if (cost instanceof ResourceCost resource && !resources.add(resource.id()))
                return DataResult.error(() -> "Duplicate resource cost " + resource.id());
            if (cost instanceof AuraCost aura && !auras.add(HolderHelper.id(aura.aura())))
                return DataResult.error(() -> "Duplicate aura cost " + HolderHelper.id(aura.aura()));
        }
        return DataResult.success(costs);
    }

    /**
     * Load-time check for the fields that are aura costs by definition ({@code cultivate_action.aura_costs},
     * spirit-crafting recipes): a resource, item or script entry there could never be paid by the store the field
     * charges, so it is refused instead of quietly never crafting.
     */
    public static DataResult<List<Cost>> validateAuras(List<Cost> costs) {
        return costs.stream().allMatch(cost -> cost instanceof AuraCost)
                ? DataResult.success(costs)
                : DataResult.error(() -> "Every entry of an aura cost list must be an mxt:aura cost");
    }

    /**
     * The aura amounts a costs array would take, evaluated only - no availability check. For the callers that pay
     * a pool or a store by hand (a per-tick cultivation settlement that holds the chunk store rather than a
     * level), and for the shared-pool allocation, which needs the amounts before it can scale them.
     * <p>
     * Null means the array is not a set of aura amounts: an entry that cannot be evaluated, or one that names a
     * value instead of an aura.
     */
    public static @Nullable Map<Holder<Aura>, Double> auras(List<Cost> costs, CostContext context) {
        CostContext auraContext = context.withAuraTarget(CostContext.AuraTarget.POOL);
        Map<Holder<Aura>, Double> amounts = new LinkedHashMap<>();
        for (Cost cost : costs) {
            Either<Charge, CostFailure> charge = cost.charge(auraContext);
            if (charge.left().isEmpty() || !(charge.left().get() instanceof Charge.Auras(
                    Map<Holder<Aura>, Double> amounts1
            ))) return null;
            amounts1.forEach((aura, amount) -> amounts.merge(aura, amount, Double::sum));
        }
        return amounts;
    }
}
