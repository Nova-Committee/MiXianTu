package com.iafenvoy.mxt.data.cost;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment.Audit;
import com.iafenvoy.mxt.data.cost.builtin.ResourceCost;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostFailure;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Evaluation;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Result;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The resource account's draft: every {@code mxt:resource} entry merges into one amount per resource in the account
 * table it holds, and the whole account is written once, through the single resource outlet that validates every
 * entry before touching any.
 *
 * <p>The table is this draft's, and the payment holds the same map: an {@code mxt:aura} entry charged as the resource
 * it is measured in lands there, and so does a caller re-pricing the payment before it pays.
 */
public final class ResourceCostDraft implements CostDraft<ResourceCost> {
    private final CostContext context;
    private final Map<Identifier, Double> account;
    private final @Nullable ResourceHolderAttachment target;
    private final @Nullable ResourceHolderAttachment baseline;
    private final Map<Holder<Resource>, Value> written = new LinkedHashMap<>();
    private @Nullable Identifier failedResource;

    public ResourceCostDraft(CostContext context, Map<Identifier, Double> account) {
        this.context = context;
        this.account = account;
        this.target = context.resourceTarget();
        // The account is copied once, here: this is what the loaded price is checked against, so a draft created on
        // demand never has to re-read the account just to answer test().
        this.baseline = this.target == null ? null : this.target.copy();
    }

    @Override
    public Optional<CostFailure> load(ResourceCost cost) {
        if (this.target == null) return Optional.of(CostFailure.NO_CHANNEL);
        // Each cost is evaluated with the formula context of the resource it spends, so a cost may refer to that
        // resource's realm rank and absorbed aura.
        FormulaContext formula = this.context.payer() == null ? this.context.formula()
                : ResourceService.formulaContext(this.context.payer(), cost.id(), this.context.formula());
        double value = cost.amount().evaluate(formula);
        if (!Double.isFinite(value) || value <= 0.0D) return Optional.of(CostFailure.INVALID_AMOUNT);
        this.account.merge(cost.id(), value, Double::sum);
        return Optional.empty();
    }

    @Override
    public Optional<CostFailure> test() {
        if (this.account.isEmpty()) return Optional.empty();
        if (this.baseline == null) return Optional.of(CostFailure.NO_PAYER);
        return this.consume(this.baseline.copy()) ? Optional.empty() : Optional.of(CostFailure.INSUFFICIENT_RESOURCE);
    }

    @Override
    public Optional<CostFailure> commit() {
        if (this.account.isEmpty()) return Optional.empty();
        if (this.target == null) return Optional.of(CostFailure.NO_PAYER);
        // What each resource it will overwrite held, and when: taken here rather than when the draft was made,
        // because a draft can wait - a cast pays at the end of its cast time - and putting an older value back
        // would undo whatever else the account gained in between.
        this.written.clear();
        for (Identifier id : this.account.keySet())
            MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, id).ifPresent(resource -> {
                Audit audit = this.target.audit(resource);
                this.written.put(resource, new Value(resource, this.target.get(resource), audit.minSnapshot(),
                        audit.maxSnapshot(), audit.lastChangedTick(), audit.source()));
            });
        return this.consume(this.target) ? Optional.empty() : Optional.of(CostFailure.INSUFFICIENT_RESOURCE);
    }

    @Override
    public void reset() {
        if (this.target == null) return;
        this.written.values().forEach(value -> this.target.set(value.resource(), value.value(), value.min(), value.max(),
                value.changedAt(), value.source()));
        this.written.clear();
    }

    @Override
    public @Nullable Identifier failedResource() {
        return this.failedResource;
    }

    // The write itself, all-or-nothing: tryConsume validates every entry before it touches any, so a store that
    // cannot pay for the whole price is left exactly as it was.
    private boolean consume(ResourceHolderAttachment holder) {
        Result result = ResourceTransactions.tryConsume(this.context.payer(), holder, new Evaluation(this.account));
        this.failedResource = result.failedResource();
        return result.committed();
    }

    private record Value(Holder<Resource> resource, double value, double min, double max, long changedAt,
                         String source) {
    }
}
