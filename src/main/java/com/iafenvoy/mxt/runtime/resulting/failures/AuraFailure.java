package com.iafenvoy.mxt.runtime.resulting.failures;

import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.saturn.runtime.resulting.SimpleFailure;

/**
 * Why an aura payment cannot be made. Saturn's {@code CostFailure} only names the channels it charges itself, so the
 * aura channel keeps its own refusal here; the entry points still answer with a value, never an exception.
 */
public final class AuraFailure {
    /**
     * The pool or bank does not hold enough of one aura.
     */
    public static final Failure INSUFFICIENT_AURA = SimpleFailure.of("insufficient_aura");

    private AuraFailure() {
    }
}
