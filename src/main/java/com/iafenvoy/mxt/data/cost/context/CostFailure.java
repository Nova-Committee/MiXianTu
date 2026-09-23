package com.iafenvoy.mxt.data.cost.context;


/**
 * Why a payment cannot be made. A payment that cannot be made is a value, never an exception: the same definition
 * must be loadable and fail cleanly at the place that tries to spend it.
 */
public enum CostFailure {
    /** A number provider produced a value that is not a finite positive amount. */
    INVALID_AMOUNT,
    /** The entry needs a payer and none was supplied. */
    NO_PAYER,
    /** The context does not offer the channel this entry needs (no bank, no position, no inventory). */
    NO_CHANNEL,
    /** The payer does not hold enough of one resource. */
    INSUFFICIENT_RESOURCE,
    /** The pool or bank does not hold enough of one aura. */
    INSUFFICIENT_AURA,
    /** The payer does not carry the matching items. */
    MISSING_ITEM,
    /** A script cost refused the payment. */
    SCRIPT_REJECTED
}
