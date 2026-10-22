package com.iafenvoy.mxt.runtime.spirit;

import com.iafenvoy.mxt.api.AuraAccess;

public final class AuraTransfers {
    private AuraTransfers() {
    }

    public static boolean covers(double available, double amount) {
        // Relative tolerance reconciles decimal accumulation without forgiving a positive fee against zero stock.
        return available >= amount || available > 0.0D
                && (amount - available) / amount <= 1.0E-12D;
    }

    public static Transfer insert(double stored, double capacity, double amount, boolean exact) {
        AuraAccess.requireNonNegative(stored);
        AuraAccess.requireNonNegative(capacity);
        AuraAccess.requireNonNegative(amount);
        double room = Math.max(0.0D, capacity - stored);
        if (exact && !covers(room, amount)) return new Transfer(stored, amount);
        double accepted = Math.min(room, amount);
        double next = Math.min(capacity, stored + accepted);
        // A request below the stored value's precision must not report a transfer without changing stock.
        if (next <= stored) return new Transfer(stored, amount);
        return new Transfer(next, covers(accepted, amount) ? 0.0D : amount - accepted);
    }

    public static Transfer extract(double stored, double amount, boolean exact) {
        AuraAccess.requireNonNegative(stored);
        AuraAccess.requireNonNegative(amount);
        if (exact && !covers(stored, amount)) return new Transfer(stored, amount);
        double taken = Math.min(stored, amount);
        // Drain rounding residue at the boundary, so repeated decimal withdrawals can reach empty.
        double next = covers(amount, stored) ? 0.0D : stored - taken;
        if (next >= stored) return new Transfer(stored, amount);
        return new Transfer(next, covers(taken, amount) ? 0.0D : amount - taken);
    }

    public static double multiply(double value, double multiplier) {
        AuraAccess.requireNonNegative(value);
        AuraAccess.requireNonNegative(multiplier);
        double result = value * multiplier;
        return Double.isFinite(result) ? result : Double.MAX_VALUE;
    }

    public record Transfer(double stored, double remainder) {
    }
}
