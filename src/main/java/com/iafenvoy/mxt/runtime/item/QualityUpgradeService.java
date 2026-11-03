package com.iafenvoy.mxt.runtime.item;

import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.quality.QualityLadders;
import com.iafenvoy.mxt.runtime.resulting.failures.QualityFailure;
import com.iafenvoy.saturn.data.cost.CostPayment;
import com.iafenvoy.saturn.data.cost.Debit;
import com.iafenvoy.saturn.data.cost.context.CostContext;
import com.iafenvoy.saturn.data.cost.context.CostOrigins;
import com.iafenvoy.saturn.profiling.ProfilerScope;
import com.iafenvoy.saturn.profiling.ServerProfiling;
import com.iafenvoy.saturn.registry.SaturnProfilerSources;
import com.iafenvoy.saturn.registry.cache.ChainCache;
import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.saturn.runtime.resulting.Failures;
import com.iafenvoy.saturn.runtime.resulting.Result;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
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

    public static Result<QualityUpgrade> upgrade(LivingEntity actor, ItemStack stack) {
        try (ProfilerScope _ = ServerProfiling.scope(SaturnProfilerSources.ITEM)) {
            if (actor.level().isClientSide()) return Result.failure(Failures.SERVER_ONLY);
            if (stack.isEmpty()) return Result.failure(QualityFailure.EMPTY);
            Provider access = actor.level().registryAccess();
            Holder<ItemQuality> current = QualityService.find(access, stack).orElse(null);
            if (current == null) return Result.failure(QualityFailure.NO_QUALITY);
            ChainCache<ItemQuality> ladders = QualityLadders.cache();
            // A tier no ladder holds has nothing to climb, and a ladder the walk refused whole leaves all of its tiers
            // out, so a broken ladder lands here too.
            if (!ladders.contains(current)) return Result.failure(QualityFailure.NO_CHAIN);
            Holder<ItemQuality> next = ladders.next(current).orElse(null);
            if (next == null) return Result.failure(QualityFailure.AT_TOP);
            ItemQuality step = next.value();
            FormulaContext formula = FormulaContext.of(actor);
            if (!step.upgradeCondition().test(actor, formula)) return Result.failure(Failures.CONDITION_FAILED);
            CostContext context = CostContext.of(actor, formula, CostOrigins.QUALITY_UPGRADE);
            Result<List<Debit>> payment = CostPayment.pay(step.upgradeCosts(), context);
            // The tier is written only after the price is actually paid, so a refusal leaves the stack untouched.
            if (payment instanceof Result.FailureResult<List<Debit>>(Failure failure))
                return Result.failure(failure);
            QualityService.set(stack, next);
            return Result.success(new QualityUpgrade(current, next));
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

    public record QualityUpgrade(Holder<ItemQuality> from, Holder<ItemQuality> to) {
    }
}