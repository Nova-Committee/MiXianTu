package com.iafenvoy.mxt.runtime.spirit;

import com.iafenvoy.mxt.api.AuraHandler;
import com.iafenvoy.mxt.data.aura.Aura;
import net.minecraft.core.Holder;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Transfers fractional aura between inventories participating in the same NeoForge transaction.
 */
public final class AuraHandlerUtil {
    private static final double MAX_ABSOLUTE_ROUNDING_ERROR = 1.0E-9D;
    private static final double MAX_RELATIVE_ROUNDING_ERROR = 1.0E-6D;

    private AuraHandlerUtil() {
    }

    public static double requireNonNegative(double amount) {
        if (!Double.isFinite(amount) || amount < 0.0D)
            throw new IllegalArgumentException("Aura amount must be finite and non-negative");
        return amount;
    }

    public static double insert(AuraHandler target, Holder<Aura> aura, double amount, boolean exact, boolean simulate,
                                @Nullable TransactionContext parent) {
        return change(target, aura, amount, exact, simulate, parent, true);
    }

    public static double extract(AuraHandler source, Holder<Aura> aura, double amount, boolean exact, boolean simulate,
                                 @Nullable TransactionContext parent) {
        return change(source, aura, amount, exact, simulate, parent, false);
    }

    private static double change(AuraHandler handler, Holder<Aura> aura, double amount, boolean exact, boolean simulate,
                                 @Nullable TransactionContext parent, boolean inserting) {
        requireNonNegative(amount);
        if (amount == 0.0D) return 0.0D;
        try (Transaction transaction = Transaction.open(parent)) {
            double before = handler.getAmount(aura);
            if (!Double.isFinite(before) || before < 0.0D) return 0.0D;
            double reported = inserting ? handler.insert(aura, amount, transaction) : handler.extract(aura, amount, transaction);
            double after = handler.getAmount(aura);
            double actual = inserting ? after - before : before - after;
            if (!validChange(actual, reported, amount, before, after)
                    || exact && !sameAmount(actual, amount, Math.max(before, after))) return 0.0D;
            if (!simulate) transaction.commit();
            return Math.min(actual, amount);
        }
    }

    /**
     * Moves as much aura as both handlers allow. The transfer joins the supplied transaction or commits independently
     * when the parent is null. Returns the moved amount.
     */
    public static double move(@Nullable AuraHandler from, @Nullable AuraHandler to, Holder<Aura> aura, double amount,
                              @Nullable TransactionContext transaction) {
        return move(from, to, aura, amount, false, false, transaction);
    }

    /**
     * Moves aura with optional exact and simulation semantics. Simulation reports the same result and rolls back
     * both inventories. Exact mode returns zero on a shortage and a positive moved amount on success.
     *
     * @param from        the source inventory or null for an unavailable source
     * @param to          the destination inventory or null for an unavailable destination
     * @param aura        the aura to move
     * @param amount      the finite, non-negative request
     * @param exact       whether to require a full transfer within the rounding tolerance
     * @param simulate    whether to roll back the transfer before returning
     * @param transaction the active parent transaction or null to open a root for the transfer
     * @return the smaller of the observed debit and credit, bounded by the request, or zero if the transfer failed
     * @throws IllegalArgumentException if the amount is negative or non-finite
     */
    public static double move(@Nullable AuraHandler from, @Nullable AuraHandler to, Holder<Aura> aura, double amount,
                              boolean exact, boolean simulate, @Nullable TransactionContext transaction) {
        requireNonNegative(amount);
        if (amount == 0.0D || from == null || to == null || from == to
                || Objects.equals(from.storageIdentity(aura), to.storageIdentity(aura))) return 0.0D;

        try (Transaction transfer = Transaction.open(transaction)) {
            double sourceBefore = from.getAmount(aura);
            double targetBefore = to.getAmount(aura);
            if (!Double.isFinite(sourceBefore) || sourceBefore <= 0.0D
                    || !Double.isFinite(targetBefore) || targetBefore < 0.0D) return 0.0D;

            double available;
            try (Transaction probe = Transaction.open(transfer)) {
                double reported = from.extract(aura, amount, probe);
                double sourceAfterProbe = from.getAmount(aura);
                available = sourceBefore - sourceAfterProbe;
                if (!validChange(available, reported, Math.min(amount, sourceBefore), sourceBefore, sourceAfterProbe))
                    return 0.0D;
            }

            double offered = Math.min(amount, available);
            double reportedInsertion = to.insert(aura, offered, transfer);
            double targetAfterInsertion = to.getAmount(aura);
            double inserted = targetAfterInsertion - targetBefore;
            if (!validChange(inserted, reportedInsertion, offered, targetBefore, targetAfterInsertion)) return 0.0D;

            double requestedExtraction = Math.min(offered, inserted);
            double reportedExtraction = from.extract(aura, requestedExtraction, transfer);
            double sourceAfter = from.getAmount(aura);
            double targetAfter = to.getAmount(aura);
            double extracted = sourceBefore - sourceAfter;
            double credited = targetAfter - targetBefore;
            double balanceScale = Math.max(Math.max(sourceBefore, sourceAfter), Math.max(targetBefore, targetAfter));
            if (!validChange(extracted, reportedExtraction, Math.min(requestedExtraction, sourceBefore), sourceBefore, sourceAfter)
                    || targetAfter != targetAfterInsertion || !sameAmount(extracted, credited, balanceScale))
                return 0.0D;
            double moved = Math.min(requestedExtraction, Math.min(extracted, credited));
            if (exact && !sameAmount(moved, amount, balanceScale))
                return 0.0D;

            if (!simulate) transfer.commit();
            return moved;
        }
    }

    private static boolean validChange(double actual, double reported, double limit, double before, double after) {
        double balanceScale = Math.max(before, after);
        return Double.isFinite(actual) && actual > 0.0D && Double.isFinite(reported) && reported > 0.0D
                && after >= 0.0D && sameAmount(actual, reported, balanceScale)
                && (actual <= limit || sameAmount(actual, limit, balanceScale));
    }

    private static boolean sameAmount(double first, double second, double balance) {
        if (first == second) return true;
        double scale = Math.max(balance, Math.max(first, second));
        // Limit tolerance to floating-point noise even for very small transfers or very large balances.
        double tolerance = Math.min(2.0D * Math.ulp(scale), MAX_ABSOLUTE_ROUNDING_ERROR);
        return Math.abs(first - second) <= Math.min(tolerance, Math.min(first, second) * MAX_RELATIVE_ROUNDING_ERROR);
    }
}
