package com.iafenvoy.mxt.data.cost.builtin;

import com.iafenvoy.mxt.api.AuraAccess;
import com.iafenvoy.mxt.attachment.AuraChunkAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.CostTarget;
import com.iafenvoy.mxt.data.cost.CostTransaction;
import com.iafenvoy.mxt.data.cost.Debit;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.resulting.ResultWithData;
import com.iafenvoy.mxt.runtime.resulting.failures.CostFailure;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.MiscUtil;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Takes aura by identity rather than by the value it is counted in: on a payer it becomes that aura's resource, in the
 * world it drains the pool at the context position, and with a bank it takes whole units out of that bank.
 * <p>
 * Which store that is belongs to the call site (see {@link CostContext}), not to this entry, so one definition can
 * charge a body, a ground or a block without saying which: a shared pool is all-or-nothing on its own, a bank is
 * drained unit by unit, and a payer target is the resource channel.
 */
public record AuraCost(Holder<Aura> aura, NumberProvider amount) implements Cost {
    public static final MapCodec<AuraCost> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Aura.CODEC.fieldOf("aura").forGetter(AuraCost::aura),
            NumberProvider.CODEC.fieldOf("amount").forGetter(AuraCost::amount)
    ).apply(i, AuraCost::new));

    @Override
    public ResultWithData<Transaction> prepare(CostContext context) {
        double value = MiscUtil.positive(this.amount().evaluate(context.formula()));
        if (value <= 0.0D) return ResultWithData.failure(null, CostFailure.INVALID_AMOUNT);
        return switch (context.auraTarget()) {
            case VALUE -> this.asResource(context, value);
            case POOL -> this.pool(context, value);
            case BANK -> this.bank(context, value);
        };
    }

    @Override
    public MapCodec<AuraCost> codec() {
        return CODEC;
    }

    // A payer carries values, not auras: what it pays is the resource this aura is measured in, so this is the resource
    // channel - which is what lets it and the entry naming that same value be one target that is charged once.
    private ResultWithData<Transaction> asResource(CostContext context, double amount) {
        return ResultWithData.success(ResourceChannel.transaction(context, this.aura.value().resource(), amount));
    }

    // The store is bound if the context has one and refused when the fee is paid otherwise: a caller reading the price
    // of an array it is not about to charge has no position and needs none.
    private ResultWithData<Transaction> pool(CostContext context, double amount) {
        Level level = context.level();
        BlockPos pos = context.pos();
        CostTarget.Section section = level == null || pos == null ? null : CostTarget.Section.of(level.dimension(), pos);
        return ResultWithData.success(new PoolTransaction(level, pos, section, this.aura, amount));
    }

    private ResultWithData<Transaction> bank(CostContext context, double amount) {
        return ResultWithData.success(new BankTransaction(context, context.bank(), this.aura, amount));
    }

    // Whole units, the only granularity a bank deals in for a display-sized price. A price a bank cannot express is
    // refused rather than clamped: taking less than the bill names while answering "paid" is a lie about the price.
    // Zero is "no amount", a negative answer is "this store cannot express the amount".
    private static int units(double amount) {
        amount = MiscUtil.positive(amount);
        if (amount <= 0.0D) return 0;
        return amount > Integer.MAX_VALUE ? -1 : (int) Math.ceil(amount);
    }

    // The shared pool at a position: one aura of one section, checked and drained all-or-nothing.
    private static final class PoolTransaction extends CostTransaction {
        private final @Nullable Level level;
        private final @Nullable BlockPos pos;
        private final @Nullable CostTarget.Section section;
        private final Holder<Aura> aura;

        private PoolTransaction(@Nullable Level level, @Nullable BlockPos pos, @Nullable CostTarget.Section section,
                                Holder<Aura> aura, double amount) {
            super(new CostTarget.AuraStore(section, aura), amount);
            this.level = level;
            this.pos = pos;
            this.section = section;
            this.aura = aura;
        }

        @Override
        protected ResultWithData<List<Debit>> perform(boolean simulate) {
            if (this.level == null || this.pos == null) return refused(CostFailure.NO_CHANNEL);
            if (simulate) {
                AuraChunkAttachment chunk = this.level.getChunkAt(this.pos).getData(MxtAttachments.AURA_CHUNK);
                boolean payable = chunk.canConsume(SectionPos.blockToSectionCoord(this.pos.getY()), this.amounts());
                return payable ? took(List.of()) : refused(CostFailure.INSUFFICIENT_AURA);
            }
            if (!AuraService.consume(this.level, this.pos, this.amounts()))
                return refused(CostFailure.INSUFFICIENT_AURA);
            return took(Debit.of(this.target(), this.amount()));
        }

        @Override
        protected CostTransaction replacing(double amount) {
            return new PoolTransaction(this.level, this.pos, this.section, this.aura, amount);
        }

        private Map<Holder<Aura>, Double> amounts() {
            return Map.of(this.aura, this.amount());
        }
    }

    // A bank is the one store another system can empty between the two passes, so a real pass asks again - and it
    // never reports a payment it did not fully get.
    private static final class BankTransaction extends CostTransaction {
        private final CostContext context;
        private final @Nullable AuraAccess bank;
        private final Holder<Aura> aura;

        private BankTransaction(CostContext context, @Nullable AuraAccess bank, Holder<Aura> aura, double amount) {
            super(new CostTarget.AuraStore(bank, aura), amount);
            this.context = context;
            this.bank = bank;
            this.aura = aura;
        }

        @Override
        protected ResultWithData<List<Debit>> perform(boolean simulate) {
            if (this.bank == null) return refused(CostFailure.NO_CHANNEL);
            int units = units(this.amount());
            if (units < 0) return refused(CostFailure.INVALID_AMOUNT);
            if (units == 0) return took(List.of());
            HolderLookup.Provider registries = this.context.registries();
            // Asked as a whole amount: a bank that can only part with some of the price cannot pay it, and a refusal
            // must not leave the part behind.
            int simulated = this.bank.extract(this.context.payer(), registries, this.aura, units, true, true);
            if (simulated != 0) return refused(CostFailure.INSUFFICIENT_AURA);
            if (simulate) return took(List.of());
            int actual = this.bank.extract(this.context.payer(), registries, this.aura, units, true, false);
            // A store that emptied between the two passes is reported as spent, not as unspent: what it did hand over
            // is kept as a debit of this fee rather than dropped with the refusal.
            if (actual != 0) return ResultWithData.failure(List.of(Debit.of(this.target(), units - actual)),
                    CostFailure.INSUFFICIENT_AURA);
            return took(Debit.of(this.target(), units));
        }

        @Override
        protected CostTransaction replacing(double amount) {
            return new BankTransaction(this.context, this.bank, this.aura, amount);
        }
    }
}
