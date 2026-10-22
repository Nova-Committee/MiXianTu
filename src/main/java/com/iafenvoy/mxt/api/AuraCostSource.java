package com.iafenvoy.mxt.api;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.runtime.resulting.ResultWithData;
import net.minecraft.core.Holder;

/**
 * Binds a fixed aura price to its payment source without checking or changing stock.
 * Implementations must preserve the physical target identity across wrappers and bind all execution inputs here.
 * The returned transaction uses the existing two-pass payment protocol and must not evaluate the price again.
 */
@FunctionalInterface
public interface AuraCostSource {
    /**
     * Binds the evaluated price, physical target, payer and registry access for both execution passes.
     *
     * @param context the context after any payer or formula overrides
     * @param aura the aura identity being charged
     * @param amount the already evaluated, finite positive amount; do not round or evaluate it again
     * @return a fixed transaction, or a refusal if the source cannot bind this price
     */
    ResultWithData<Cost.Transaction> bind(CostContext context, Holder<Aura> aura, double amount);
}
