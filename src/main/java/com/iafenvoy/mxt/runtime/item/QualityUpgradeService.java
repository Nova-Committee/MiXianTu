package com.iafenvoy.mxt.runtime.item;

import com.iafenvoy.mxt.data.cost.CostTransaction;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostFailure;
import com.iafenvoy.mxt.data.cost.context.CostOrigin;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.quality.QualityLadders;
import com.iafenvoy.mxt.util.ChainCache;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

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

    public static Result upgrade(LivingEntity actor, ItemStack stack) {
        if (actor.level().isClientSide()) return Result.rejected(Failure.SERVER_ONLY);
        if (stack.isEmpty()) return Result.rejected(Failure.EMPTY);
        Provider access = actor.level().registryAccess();
        Holder<ItemQuality> current = ItemQualityService.find(access, stack).orElse(null);
        if (current == null) return Result.rejected(Failure.NO_QUALITY);
        ChainCache<ItemQuality> ladders = QualityLadders.cache(access);
        // A tier no ladder holds has nothing to climb, and a ladder the walk refused whole leaves all of its tiers
        // out, so a broken ladder lands here too.
        if (!ladders.contains(HolderHelper.id(current))) return Result.rejected(Failure.NO_CHAIN);
        Holder<ItemQuality> next = ladders.next(HolderHelper.id(current)).orElse(null);
        if (next == null) return Result.rejected(Failure.AT_TOP);
        ItemQuality step = next.value();
        FormulaContext formula = FormulaContext.of(actor);
        if (!step.upgradeCondition().test(actor, formula)) return Result.rejected(Failure.CONDITION_FAILED);
        CostContext context = CostContext.of(actor, formula, CostOrigin.QUALITY_UPGRADE);
        CostTransaction.Planning plan = CostTransaction.plan(step.upgradeCosts(), context);
        if (!plan.ok()) return Result.rejected(costFailure(plan.failure()));
        CostTransaction.PayResult payment = CostTransaction.commit(plan, context);
        // The tier is written only after the price is actually paid, so a refusal leaves the stack untouched.
        if (!payment.paid()) return Result.rejected(costFailure(payment.failure()));
        ItemQualityService.set(stack, next);
        return Result.upgraded(current, next);
    }

    public static boolean canUpgrade(LivingEntity actor, ItemStack stack) {
        Provider access = actor.level().registryAccess();
        Holder<ItemQuality> current = ItemQualityService.find(access, stack).orElse(null);
        return current != null && QualityLadders.cache(access).next(HolderHelper.id(current)).isPresent();
    }

    // The tier the ladder would move to, for a caller that wants to show it before anything is paid.
    public static Optional<Holder<ItemQuality>> nextTier(Provider access, ItemStack stack) {
        return ItemQualityService.find(access, stack)
                .flatMap(current -> QualityLadders.cache(access).next(HolderHelper.id(current)));
    }

    // Every cost failure ends the same way for the caller: a resource that ran out is named, anything else is
    // "this cannot be paid for".
    private static Failure costFailure(CostFailure failure) {
        return failure == CostFailure.INSUFFICIENT_RESOURCE ? Failure.INSUFFICIENT_RESOURCE : Failure.INSUFFICIENT_COST;
    }

    public enum Failure {
        SERVER_ONLY, EMPTY, NO_QUALITY, NO_CHAIN, AT_TOP,
        CONDITION_FAILED, INSUFFICIENT_RESOURCE, INSUFFICIENT_COST
    }

    public record Result(boolean changed, Failure failure, Holder<ItemQuality> from, Holder<ItemQuality> to) {
        private static Result upgraded(Holder<ItemQuality> from, Holder<ItemQuality> to) {
            return new Result(true, null, from, to);
        }

        private static Result rejected(Failure failure) {
            return new Result(false, failure, null, null);
        }
    }
}
