package com.iafenvoy.mxt.data.cost;

import com.iafenvoy.mxt.api.AuraAccess;
import com.iafenvoy.mxt.attachment.AuraChunkAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.builtin.AuraCost;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostFailure;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The aura draft: what every {@code mxt:aura} entry takes from the store its context names, merged per aura in the
 * table it holds.
 *
 * <p>Which store that is belongs to the call site, not to the entry: a shared pool at the context position is
 * all-or-nothing on its own, a block entity's bank is drained unit by unit and put back unit by unit, and a payer
 * target turns the aura into the resource it is measured in, which goes into the payment's account table - the one
 * the resource draft charges - instead.
 */
public final class AuraCostDraft implements CostDraft<AuraCost> {
    private final CostContext context;
    private final Map<Holder<Aura>, Double> auras = new LinkedHashMap<>();
    private final Map<Identifier, Double> account;
    private final Map<Holder<Aura>, Integer> taken = new LinkedHashMap<>();
    private final Map<Holder<Aura>, Double> refund = new LinkedHashMap<>();

    public AuraCostDraft(CostContext context, Map<Identifier, Double> account) {
        this.context = context;
        this.account = account;
    }

    @Override
    public Map<Holder<Aura>, Double> auras() {
        return this.auras;
    }

    @Override
    public Optional<CostFailure> load(AuraCost cost) {
        double value = cost.amount().evaluate(this.context.formula());
        if (!Double.isFinite(value) || value <= 0.0D) return Optional.of(CostFailure.INVALID_AMOUNT);
        if (this.context.auraTarget() == CostContext.AuraTarget.VALUE) {
            this.account.merge(HolderHelper.id(cost.aura().value().resource()), value, Double::sum);
            return Optional.empty();
        }
        this.auras.merge(cost.aura(), value, Double::sum);
        return Optional.empty();
    }

    @Override
    public Optional<CostFailure> test() {
        if (this.auras.isEmpty()) return Optional.empty();
        return switch (this.context.auraTarget()) {
            case BANK -> this.bankTest();
            case POOL -> this.poolTest();
            // A payer target turns aura into the resource it is measured in, so there is no aura left to ask about.
            case VALUE -> Optional.empty();
        };
    }

    @Override
    public Optional<CostFailure> commit() {
        if (this.auras.isEmpty()) return Optional.empty();
        return switch (this.context.auraTarget()) {
            case BANK -> this.bankCommit();
            case POOL -> this.poolCommit();
            case VALUE -> Optional.empty();
        };
    }

    @Override
    public void reset() {
        // Only what was actually taken: a bank commit that refused halfway left units here, and they belong back
        // where they came from.
        if (!this.taken.isEmpty()) {
            AuraAccess bank = this.context.bank();
            if (bank != null)
                this.taken.forEach((aura, units) -> bank.insert(this.context.payer(), aura, units, false));
            this.taken.clear();
        }
        if (!this.refund.isEmpty()) {
            AuraService.change(this.context.level(), this.context.pos(), this.refund);
            this.refund.clear();
        }
    }

    private Optional<CostFailure> bankTest() {
        AuraAccess bank = this.context.bank();
        if (bank == null) return Optional.of(CostFailure.NO_CHANNEL);
        for (Map.Entry<Holder<Aura>, Double> entry : this.auras.entrySet()) {
            int units = units(entry.getValue());
            if (units <= 0) return Optional.of(CostFailure.INVALID_AMOUNT);
            if (bank.extract(this.context.payer(), entry.getKey(), units, true) != 0)
                return Optional.of(CostFailure.INSUFFICIENT_AURA);
        }
        return Optional.empty();
    }

    private Optional<CostFailure> bankCommit() {
        AuraAccess bank = this.context.bank();
        if (bank == null) return Optional.of(CostFailure.NO_CHANNEL);
        for (Map.Entry<Holder<Aura>, Double> entry : this.auras.entrySet()) {
            int units = units(entry.getValue());
            if (units <= 0) return Optional.of(CostFailure.INVALID_AMOUNT);
            // A bank is the one store another system can empty between test() and here, so every extraction asks
            // again, and what was already taken is handed back by reset().
            if (bank.extract(this.context.payer(), entry.getKey(), units, true) != 0)
                return Optional.of(CostFailure.INSUFFICIENT_AURA);
            bank.extract(this.context.payer(), entry.getKey(), units, false);
            this.taken.merge(entry.getKey(), units, Integer::sum);
        }
        return Optional.empty();
    }

    private Optional<CostFailure> poolTest() {
        Level level = this.context.level();
        BlockPos pos = this.context.pos();
        if (level == null || pos == null) return Optional.of(CostFailure.NO_CHANNEL);
        AuraChunkAttachment chunk = level.getChunkAt(pos).getData(MxtAttachments.AURA_CHUNK);
        return chunk.canConsume(this.auras) ? Optional.empty() : Optional.of(CostFailure.INSUFFICIENT_AURA);
    }

    private Optional<CostFailure> poolCommit() {
        Level level = this.context.level();
        BlockPos pos = this.context.pos();
        if (level == null || pos == null) return Optional.of(CostFailure.NO_CHANNEL);
        if (!AuraService.consume(level, pos, this.auras)) return Optional.of(CostFailure.INSUFFICIENT_AURA);
        // The pool took the whole price or none of it, and what it took is what a later refusal hands back.
        this.refund.putAll(this.auras);
        return Optional.empty();
    }

    // Whole units, the only granularity a bank deals in for a display-sized price.
    private static int units(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0D) return 0;
        return amount >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.ceil(amount);
    }
}
