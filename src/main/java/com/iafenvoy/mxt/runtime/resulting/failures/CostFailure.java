package com.iafenvoy.mxt.runtime.resulting.failures;

import com.iafenvoy.mxt.runtime.resulting.Failure;
import com.iafenvoy.mxt.runtime.resulting.SimpleFailure;

/**
 * Why a payment cannot be made. A payment that cannot be made is a value, never an exception: the same definition
 * must be loadable and fail cleanly at the place that tries to spend it.
 */
public final class CostFailure {
    /**
     * A number provider produced a value that is not a finite positive amount.
     */
    public static final Failure INVALID_AMOUNT = SimpleFailure.of("invalid_amount");
    /**
     * The entry needs a payer and none was supplied.
     */
    public static final Failure NO_PAYER = SimpleFailure.of("no_payer");
    /**
     * The context does not offer the channel this entry needs (no bank, no position, no inventory).
     */
    public static final Failure NO_CHANNEL = SimpleFailure.of("no_channel");
    /**
     * The pool or bank does not hold enough of one aura.
     */
    public static final Failure INSUFFICIENT_AURA = SimpleFailure.of("insufficient_aura");
    /**
     * The payer does not carry the matching items.
     */
    public static final Failure MISSING_ITEM = SimpleFailure.of("missing_item");
    /**
     * A script cost refused the payment.
     */
    public static final Failure SCRIPT_REJECTED = SimpleFailure.of("script_rejected");
    /**
     * The bill was already paid, so it can no longer be priced into, edited or paid again.
     */
    public static final Failure BILL_CLOSED = SimpleFailure.of("bill_closed");
    /**
     * A payment reached one of its own stores again while it was writing it.
     */
    public static final Failure REENTRANT = SimpleFailure.of("reentrant");

    private CostFailure() {
    }
}
