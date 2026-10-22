package com.iafenvoy.mxt.runtime.item;

import com.iafenvoy.mxt.data.cost.CostPayment;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostOrigins;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.quality.QualityLadders;
import com.iafenvoy.mxt.registry.cache.ChainCache;
import com.iafenvoy.mxt.runtime.resulting.Failure;
import com.iafenvoy.mxt.runtime.resulting.Failures;
import com.iafenvoy.mxt.runtime.resulting.Result;
import com.iafenvoy.mxt.runtime.resulting.failures.QualityFailure;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.profiling.ProfilerScope;
import com.iafenvoy.mxt.util.profiling.ServerProfiling;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Moving an item one step up its quality ladder. The ladder is the one the stack is read on - what it carries,
 * else what its binding declares; what the step costs and when it may be taken is written on the tier it leads
 * to, so a step nobody priced cannot be taken at all and a ladder used purely for ordering stays that way.
 *
 * <p>Server side only, and one step per call: the price of a skipped step would be a sum nobody wrote down. Every
 * refusal is a value rather than an exception, because "why did nothing happen" is the interesting part.
 */
public final class QualityUpgradeService {
    private QualityUpgradeService() {
    }

    public static UpgradeResult upgrade(LivingEntity actor, ItemStack stack) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.ITEM)) {
            if (actor.level().isClientSide()) return UpgradeResult.rejected(Failures.SERVER_ONLY);
            if (stack.isEmpty()) return UpgradeResult.rejected(QualityFailure.EMPTY);
            Provider access = actor.level().registryAccess();
            Holder<ItemQuality> current = QualityService.find(access, stack).orElse(null);
            if (current == null) return UpgradeResult.rejected(QualityFailure.NO_QUALITY);
            ChainCache<ItemQuality> ladders = QualityLadders.cache();
            // A tier no ladder holds has nothing to climb, and a ladder the walk refused whole leaves all of its tiers
            // out, so a broken ladder lands here too.
            if (!ladders.contains(current)) return UpgradeResult.rejected(QualityFailure.NO_CHAIN);
            Holder<ItemQuality> next = ladders.next(current).orElse(null);
            if (next == null) return UpgradeResult.rejected(QualityFailure.AT_TOP);
            ItemQuality step = next.value();
            FormulaContext formula = FormulaContext.of(actor);
            if (!step.upgradeCondition().test(actor, formula)) return UpgradeResult.rejected(Failures.CONDITION_FAILED);
            CostContext context = CostContext.of(actor, formula, CostOrigins.QUALITY_UPGRADE);
            CostPayment.PaymentResult payment = CostPayment.pay(step.upgradeCosts(), context);
            // The tier is written only after the price is actually paid, so a refusal leaves the stack untouched.
            if (!payment.isSuccess()) return UpgradeResult.rejected(payment.failure());
            QualityService.set(stack, next);
            return UpgradeResult.upgraded(current, next);
        }
    }

    public static boolean canUpgrade(LivingEntity actor, ItemStack stack) {
        Provider access = actor.level().registryAccess();
        Holder<ItemQuality> current = QualityService.find(access, stack).orElse(null);
        return current != null && QualityLadders.cache().next(current).isPresent();
    }

    // The tier the ladder would move to, for a caller that wants to show it before anything is paid.
    public static Optional<Holder<ItemQuality>> nextTier(Provider access, ItemStack stack) {
        return QualityService.find(access, stack).flatMap(current -> QualityLadders.cache().next(current));
    }

    public static final class UpgradeResult extends Result {
        private final @Nullable Holder<ItemQuality> from;
        private final @Nullable Holder<ItemQuality> to;

        private UpgradeResult(@Nullable Holder<ItemQuality> from, @Nullable Holder<ItemQuality> to, Failure failure) {
            super(failure, null);
            this.from = from;
            this.to = to;
        }

        private static UpgradeResult upgraded(Holder<ItemQuality> from, Holder<ItemQuality> to) {
            return new UpgradeResult(from, to, null);
        }

        private static UpgradeResult rejected(Failure failure) {
            return new UpgradeResult(null, null, failure);
        }

        public @Nullable Holder<ItemQuality> from() {
            return this.from;
        }

        public @Nullable Holder<ItemQuality> to() {
            return this.to;
        }
    }
}
