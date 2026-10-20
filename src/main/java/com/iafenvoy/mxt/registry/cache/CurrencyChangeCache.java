package com.iafenvoy.mxt.registry.cache;

import com.iafenvoy.mxt.runtime.economy.CurrencyPaymentService;
import net.minecraft.core.RegistryAccess;

import java.util.List;
import java.util.OptionalLong;

/**
 * The denomination set a counter pays change out of, checked when the pack loads.
 * <p>
 * Change is made greedily, which is only correct for a set the greedy answer always solves. A set that is not - values
 * 4 and 3 cannot pay 6 the greedy way although 3 + 3 can - is reported at load time instead of surfacing as a cheque that
 * cannot be cashed at a counter that looks like it should. It is a report and not a refusal: the rest of the pack is
 * still usable, and an author needs the message to fix the set.
 */
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
        LOGGER.error("The currency denominations cannot make change greedily: {} is reachable but not by always taking the largest denomination that fits", this.firstFailure.orElse(-1));
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

    /**
     * Whether this pack's denominations can be paid out by taking the largest denomination that fits, which is what a
     * counter does when it hands a cheque's value back as items.
     */
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
