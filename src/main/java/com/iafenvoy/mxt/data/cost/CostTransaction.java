package com.iafenvoy.mxt.data.cost;

import com.iafenvoy.mxt.runtime.resulting.Failure;
import com.iafenvoy.mxt.runtime.resulting.ResultWithData;
import com.iafenvoy.mxt.runtime.resulting.failures.CostFailure;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The execution skeleton every bound fee shares. A transaction is a fixed target at a fixed amount: asked whether it
 * can be paid it writes nothing, asked to pay it writes its own channel - and the first real answer is the one it
 * keeps, so calling it twice cannot charge twice.
 *
 * <p>The amount is copied, never read back from the definition: a replacement is a new transaction with the same
 * binding, which is how a payment re-prices a fee without re-running the formula that priced it.
 *
 * <p>The re-entrancy guard lives here rather than in a payment, so a script that executes a transaction directly is
 * guarded the same way a bill is: the stores being written on this thread are one set, and a second pass over one of
 * them is refused instead of interleaved.
 */
public abstract class CostTransaction implements Cost.Transaction {
    private static final ThreadLocal<Set<Object>> WRITING = ThreadLocal.withInitial(HashSet::new);
    private final CostTarget target;
    private final double amount;
    private @Nullable ResultWithData<List<Debit>> taken;
    private boolean writing;

    protected CostTransaction(CostTarget target, double amount) {
        this.target = target;
        this.amount = amount;
    }

    @Override
    public final CostTarget target() {
        return this.target;
    }

    @Override
    public final double amount() {
        return this.amount;
    }

    @Override
    public final ResultWithData<List<Debit>> execute(boolean simulate) {
        // A settled transaction answers with what it took, and a simulation of it is still a simulation: only a real
        // pass produces debits, whichever order the two were asked in.
        if (this.taken != null) return simulate ? withoutDebits(this.taken) : this.taken;
        // A real pass began and never answered - it threw rather than returned, so what it wrote before that cannot be
        // taken back and cannot be known here. Executing again could charge a fee that is already paid.
        if (this.writing) return ResultWithData.failure(List.of(), CostFailure.UNCERTAIN);
        Object guard = this.target.guard();
        Set<Object> writing = WRITING.get();
        if (!writing.add(guard)) return ResultWithData.failure(List.of(), CostFailure.REENTRANT);
        try {
            if (!simulate) this.writing = true;
            ResultWithData<List<Debit>> result = this.perform(simulate);
            // A simulated answer is a check of what the store holds right now, so it is not kept: the same channel can
            // be asked again after the inventory moved. A real one is terminal.
            if (!simulate) {
                this.writing = false;
                this.taken = result;
            }
            return result;
        } finally {
            writing.remove(guard);
            if (writing.isEmpty()) WRITING.remove();
        }
    }

    // The same answer without the real charges it reports: a simulation never writes, so it never took anything.
    private static ResultWithData<List<Debit>> withoutDebits(ResultWithData<List<Debit>> taken) {
        return taken.isFailure()
                ? ResultWithData.failure(List.of(), taken.failure(), taken.failedResource())
                : ResultWithData.success(List.of());
    }

    @Override
    public final Cost.Transaction withAmount(double amount) {
        return this.replacing(amount);
    }

    /**
     * One pass over this channel: {@code simulate} asks and writes nothing, otherwise the fee is written and answered
     * with what it took.
     */
    protected abstract ResultWithData<List<Debit>> perform(boolean simulate);

    /**
     * The same binding at another fixed amount, with no execution state of its own yet.
     */
    protected abstract CostTransaction replacing(double amount);

    protected static ResultWithData<List<Debit>> took(Debit debit) {
        return ResultWithData.success(List.of(debit));
    }

    protected static ResultWithData<List<Debit>> took(List<Debit> debits) {
        return ResultWithData.success(debits);
    }

    protected static ResultWithData<List<Debit>> refused(Failure failure) {
        return ResultWithData.failure(List.of(), failure);
    }

    protected static ResultWithData<List<Debit>> refused(Failure failure, @Nullable Identifier resource) {
        return ResultWithData.failure(List.of(), failure, resource);
    }
}
