package com.iafenvoy.mxt.data.cost.builtin;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.cost.CostTarget;
import com.iafenvoy.mxt.data.cost.CostTransaction;
import com.iafenvoy.mxt.data.cost.Debit;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Evaluation;
import com.iafenvoy.mxt.runtime.resulting.Failures;
import com.iafenvoy.mxt.runtime.resulting.ResultWithData;
import com.iafenvoy.mxt.runtime.resulting.failures.CostFailure;
import com.iafenvoy.mxt.util.MiscUtil;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * The one resource channel, shared by the entry that names a resource and the aura entry charged as the resource it is
 * measured in: one balance, one write, one debit.
 */
public final class ResourceChannel {
    private ResourceChannel() {
    }

    // Each resource amount is evaluated with the formula context of the resource it spends, so a cost may refer to that
    // resource's own realm rank and absorbed aura.
    static double evaluate(CostContext context, Holder<Resource> resource, NumberProvider amount) {
        FormulaContext formula = context.payer() == null ? context.formula()
                : ResourceService.formulaContext(context.payer(), resource, context.formula());
        return MiscUtil.positive(amount.evaluate(formula));
    }

    /**
     * A fixed amount of one resource out of the account this context names, asked against the payer. An account the
     * context does not have is bound as absent and refused when the fee is paid, because a price can be read without
     * one.
     */
    public static CostTransaction transaction(CostContext context, Holder<Resource> resource, double amount) {
        return new ResourceTransaction(context, context.resourceTarget(), resource, amount);
    }

    // The write is tryConsume's: it validates the whole amount before touching anything, so one fee is all or nothing.
    // A simulated pass runs it against a copy, which is what makes asking free.
    private static final class ResourceTransaction extends CostTransaction {
        private final CostContext context;
        private final @Nullable ResourceHolderAttachment account;
        private final Holder<Resource> resource;

        private ResourceTransaction(CostContext context, @Nullable ResourceHolderAttachment account,
                                    Holder<Resource> resource, double amount) {
            super(new CostTarget.Account(account, resource), amount);
            this.context = context;
            this.account = account;
            this.resource = resource;
        }

        @Override
        protected ResultWithData<List<Debit>> perform(boolean simulate) {
            if (this.account == null) return refused(CostFailure.NO_CHANNEL);
            LivingEntity payer = this.context.payer();
            ResultWithData<Map<Identifier, Double>> result = ResourceTransactions.tryConsume(payer,
                    simulate ? this.account.copy() : this.account,
                    new Evaluation(Map.of(this.resource, this.amount())));
            if (result.isFailure()) return refused(Failures.INSUFFICIENT_RESOURCE, result.failedResource());
            // A check takes nothing, so it has nothing to report: the write is the account's, and the debit names what
            // that write really left behind rather than what was asked for.
            if (simulate) return took(List.of());
            return took(Debit.of(this.target(), this.amount()));
        }

        @Override
        protected CostTransaction replacing(double amount) {
            return new ResourceTransaction(this.context, this.account, this.resource, amount);
        }
    }
}
