package com.iafenvoy.mxt.data.cost.context;

import com.iafenvoy.mxt.api.AuraCostSource;
import com.iafenvoy.mxt.api.AuraHandler;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.AuraCostTarget;
import com.iafenvoy.mxt.runtime.resulting.failures.AuraFailure;
import com.iafenvoy.mxt.runtime.spirit.AuraHandlerUtil;
import com.iafenvoy.mxt.runtime.world.WorldAuraAccess;
import com.iafenvoy.saturn.data.cost.CostTransaction;
import com.iafenvoy.saturn.data.cost.Debit;
import com.iafenvoy.saturn.data.cost.builtin.ResourceChannel;
import com.iafenvoy.saturn.runtime.resulting.Result;
import com.iafenvoy.saturn.runtime.resulting.failures.CostFailure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class AuraCostSources {
    // Read the payer at bind time: withPayer must not leave a source pointing at the previous account.
    public static final AuraCostSource ACCOUNT = (context, aura, amount) -> Result.success(ResourceChannel.transaction(context, aura.value().resource(), amount));
    // Quotes carry an amount but deliberately have no inventory to execute against.
    public static final AuraCostSource QUOTE = (context, aura, amount) -> Result.success(new StoreTransaction(null, aura, amount));

    private AuraCostSources() {
    }

    public static AuraCostSource pool(Level level, BlockPos pos) {
        return handler(new WorldAuraAccess(level, pos));
    }

    public static AuraCostSource handler(AuraHandler handler) {
        return (context, aura, amount) -> Result.success(new StoreTransaction(handler, aura, amount));
    }

    private static final class StoreTransaction extends CostTransaction {
        private final @Nullable AuraHandler handler;
        private final Holder<Aura> aura;

        private StoreTransaction(@Nullable AuraHandler handler, Holder<Aura> aura, double amount) {
            this(handler, new AuraCostTarget(handler == null ? null : handler.storageIdentity(aura), aura), amount);
        }

        private StoreTransaction(@Nullable AuraHandler handler, AuraCostTarget target, double amount) {
            super(target, amount);
            this.handler = handler;
            this.aura = target.aura();
        }

        @Override
        protected Result<List<Debit>> perform(boolean simulate) {
            if (this.handler == null) return refused(CostFailure.NO_CHANNEL);
            double amount = this.amount();
            if (!Double.isFinite(amount) || amount < 0.0D) return refused(CostFailure.INVALID_AMOUNT);
            if (amount == 0.0D) return took(List.of());
            double taken = AuraHandlerUtil.extract(this.handler, this.aura, amount, true, simulate, null);
            if (taken == 0.0D) return refused(AuraFailure.INSUFFICIENT_AURA);
            return took(simulate ? List.of() : List.of(Debit.of(this.target(), taken)));
        }

        @Override
        protected CostTransaction replacing(double amount) {
            return new StoreTransaction(this.handler, (AuraCostTarget) this.target(), amount);
        }
    }
}
