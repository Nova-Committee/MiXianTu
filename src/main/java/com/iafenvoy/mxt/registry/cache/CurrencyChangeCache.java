package com.iafenvoy.mxt.registry.cache;

import com.iafenvoy.mxt.runtime.economy.CurrencyPaymentService;
import net.minecraft.core.RegistryAccess;

import java.util.List;
import java.util.OptionalLong;

// Load-time greedy diagnostics do not restrict the exact payout planner.
public final class CurrencyChangeCache extends RegistryCache<CurrencyChangeCache> {
    private volatile List<Long> values = List.of();
    private volatile OptionalLong firstFailure = OptionalLong.empty();

    public static CurrencyChangeCache of() {
        return new CurrencyChangeCache();
    }

    @Override
    protected void build(RegistryAccess access) {
        // Read from the definitions, not from a stack: this runs while the tags are being updated, which is before
        // item components are bound, so no ItemStack may be built here.
        List<Long> declared = CurrencyPaymentService.declaredValues(access);
        this.values = declared;
        long bound = CurrencyPaymentService.checkBound(declared);
        this.firstFailure = CurrencyPaymentService.greedyFailure(declared, bound);
        if (this.firstFailure.isEmpty()) return;
        LOGGER.warn("Greedy currency change leaves a remainder at {}; exact change planning will search for a combination within the available slots", this.firstFailure.orElse(-1));
    }

    @Override
    protected void clear() {
        this.values = List.of();
        this.firstFailure = OptionalLong.empty();
    }

    @Override
    public CurrencyChangeCache get() {
        return this;
    }

    // No counterexample in the checked interval; this does not certify other values or slot budgets.
    public boolean solvable() {
        return this.firstFailure.isEmpty();
    }

    /**
     * The first value this set cannot make change for greedily, for the load-time report.
     */
    public OptionalLong firstFailure() {
        return this.firstFailure;
    }

    /**
     * The declared values, largest first.
     */
    public List<Long> values() {
        return this.values;
    }
}
