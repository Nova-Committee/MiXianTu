package com.iafenvoy.mxt.data.cost;

import com.iafenvoy.mxt.runtime.resulting.Failure;
import com.iafenvoy.mxt.runtime.resulting.ResultWithData;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The execution skeleton every bound fee shares. A transaction is a fixed target at a fixed amount: asked whether it
 * can be paid it writes nothing, asked to pay it writes its own channel - and the first real answer is the one it
 * keeps, so calling it twice cannot charge twice.
 *
 * <p>The amount is copied, never read back from the definition: a replacement is a new transaction with the same
 * binding, which is how a payment re-prices a fee without re-running the formula that priced it.
 */
public abstract class CostTransaction implements Cost.Transaction {
    private final CostTarget target;
    private final double amount;
    private @Nullable ResultWithData<List<Debit>> taken;

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
        if (this.taken != null) return this.taken;
        ResultWithData<List<Debit>> result = this.perform(simulate);
        // A simulated answer is a check of what the store holds right now, so it is not kept: the same channel can be
        // asked again after the inventory moved. A real one is terminal.
        if (!simulate) this.taken = result;
        return result;
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
