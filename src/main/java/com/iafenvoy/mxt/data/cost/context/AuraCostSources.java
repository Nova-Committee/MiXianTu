package com.iafenvoy.mxt.data.cost.context;

import com.iafenvoy.mxt.api.AuraAccess;
import com.iafenvoy.mxt.api.AuraCostSource;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.CostTarget;
import com.iafenvoy.mxt.data.cost.CostTransaction;
import com.iafenvoy.mxt.data.cost.Debit;
import com.iafenvoy.mxt.data.cost.builtin.ResourceChannel;
import com.iafenvoy.mxt.runtime.resulting.ResultWithData;
import com.iafenvoy.mxt.runtime.resulting.failures.CostFailure;
import com.iafenvoy.mxt.runtime.world.WorldAuraAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class AuraCostSources {
    // Read the payer at bind time: withPayer must not leave a source pointing at the previous account.
    public static final AuraCostSource ACCOUNT = (context, aura, amount) -> ResultWithData.success(ResourceChannel.transaction(context, aura.value().resource(), amount));
    // Quotes carry an amount but deliberately have no inventory to execute against.
    public static final AuraCostSource QUOTE = store(null, null);

    private AuraCostSources() {
    }

    public static AuraCostSource pool(Level level, BlockPos pos) {
        // Different positions in one section still spend the same stock.
        return store(new WorldAuraAccess(level, pos), CostTarget.Section.of(level.dimension(), pos));
    }

    public static AuraCostSource bank(AuraAccess access) {
        return store(access, access);
    }

    private static AuraCostSource store(@Nullable AuraAccess access, @Nullable Object identity) {
        // access performs transfers; identity names the stock for duplicate checks and re-entrancy guards.
        // A bank uses its original object, a pool uses section coordinates, and a quote has no identity.
        return (context, aura, amount) -> ResultWithData.success(new StoreTransaction(access, identity, context.payer(), context.registries(), aura, amount));
    }

    private static final class StoreTransaction extends CostTransaction {
        private final @Nullable AuraAccess access;
        private final @Nullable LivingEntity payer;
        private final Provider registries;
        private final Holder<Aura> aura;

        private StoreTransaction(@Nullable AuraAccess access, @Nullable Object identity, @Nullable LivingEntity payer, Provider registries, Holder<Aura> aura, double amount) {
            super(new CostTarget.AuraStore(identity, aura), amount);
            this.access = access;
            this.payer = payer;
            this.registries = registries;
            this.aura = aura;
        }

        @Override
        protected ResultWithData<List<Debit>> perform(boolean simulate) {
            if (this.access == null) return refused(CostFailure.NO_CHANNEL);
            double amount = this.amount();
            if (!Double.isFinite(amount) || amount < 0.0D) return refused(CostFailure.INVALID_AMOUNT);
            if (amount == 0.0D) return took(List.of());
            // Recheck even on the real pass: earlier fees may have spent the same stock since simulation.
            double remainder = this.access.extract(this.payer, this.registries, this.aura, amount, true, true);
            if (remainder != 0.0D) return refused(CostFailure.INSUFFICIENT_AURA);
            if (simulate) return took(List.of());
            remainder = this.access.extract(this.payer, this.registries, this.aura, amount, true, false);
            if (!Double.isFinite(remainder) || remainder < 0.0D || remainder > amount)
                return refused(CostFailure.INVALID_AMOUNT);
            // A faulty external store may violate exact; preserve its known partial debit for the payment's WARN.
            double taken = amount - remainder;
            List<Debit> debits = taken > 0.0D ? List.of(Debit.of(this.target(), taken)) : List.of();
            return remainder == 0.0D ? took(debits) : ResultWithData.failure(debits, CostFailure.INSUFFICIENT_AURA);
        }

        @Override
        protected CostTransaction replacing(double amount) {
            return new StoreTransaction(this.access, ((CostTarget.AuraStore) this.target()).store(), this.payer, this.registries, this.aura, amount);
        }
    }
}
