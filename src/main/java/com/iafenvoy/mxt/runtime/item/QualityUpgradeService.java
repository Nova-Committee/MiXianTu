package com.iafenvoy.mxt.runtime.item;

import com.iafenvoy.mxt.data.cost.CostTransaction;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostFailure;
import com.iafenvoy.mxt.data.cost.context.CostOrigin;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.quality.QualityChain;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Moving an item one step up its quality ladder. What each step costs and when it may be taken is the pack's own
 * declaration; a step nobody declared cannot be taken at all, so a ladder used purely for ordering stays that way.
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
        // The ladder has to be unambiguous before anything else is asked: two ladders holding one tier leave "where
        // does this climb to" unanswered, which is a different answer from "it is on no ladder at all".
        Optional<Holder<QualityChain>> declared = ItemBindingService.qualityChain(access, stack);
        Holder<QualityChain> chain = declared.orElse(null);
        if (chain == null) {
            List<Holder<QualityChain>> candidates = QualityChainService.chainsOf(access, current);
            if (candidates.isEmpty()) return Result.rejected(Failure.NO_CHAIN);
            if (candidates.size() > 1) return Result.rejected(Failure.AMBIGUOUS_CHAIN);
            chain = candidates.getFirst();
        }
        QualityChain ladder = chain.value();
        Holder<ItemQuality> next = ladder.nextTier(current).orElse(null);
        // Not being a member means the item's tier did not come from this ladder at all, which is a different
        // answer from "already at the top".
        if (next == null) return Result.rejected(ladder.isMember(current) ? Failure.AT_TOP : Failure.NOT_MEMBER);
        if (MxtDatapackRegistries.isDisabled(MxtResourceKeys.ITEM_QUALITY, next))
            return Result.rejected(Failure.DISABLED);
        QualityChain.Step step = ladder.stepUp(current).orElse(null);
        if (step == null) return Result.rejected(Failure.NO_STEP);
        FormulaContext formula = FormulaContext.of(actor);
        if (!step.condition().test(actor, formula)) return Result.rejected(Failure.CONDITION_FAILED);
        CostContext context = CostContext.of(actor, formula, CostOrigin.QUALITY_UPGRADE);
        CostTransaction.Planning plan = CostTransaction.plan(step.costs(), context);
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
        if (current == null) return false;
        Holder<QualityChain> chain = chain(access, stack, current);
        return chain != null && chain.value().stepUp(current).isPresent() && chain.value().nextTier(current).isPresent();
    }

    // The tier the ladder would move to, for a caller that wants to show it before anything is paid.
    public static Optional<Holder<ItemQuality>> nextTier(Provider access, ItemStack stack) {
        return ItemQualityService.find(access, stack)
                .flatMap(current -> Optional.ofNullable(chain(access, stack, current))
                        .flatMap(chain -> chain.value().nextTier(current)));
    }

    // The ladder to climb when the caller only wants to know whether one exists: the declared one, or the single
    // ladder holding the tier. Ambiguity reads as "no ladder" here, because a preview has nothing to refuse with.
    private static @Nullable Holder<QualityChain> chain(Provider access, ItemStack stack, Holder<ItemQuality> current) {
        Optional<Holder<QualityChain>> declared = ItemBindingService.qualityChain(access, stack);
        if (declared.isPresent()) return declared.orElseThrow();
        List<Holder<QualityChain>> candidates = QualityChainService.chainsOf(access, current);
        return candidates.size() == 1 ? candidates.getFirst() : null;
    }

    // Every cost failure ends the same way for the caller: a resource that ran out is named, anything else is
    // "this cannot be paid for".
    private static Failure costFailure(CostFailure failure) {
        return failure == CostFailure.INSUFFICIENT_RESOURCE ? Failure.INSUFFICIENT_RESOURCE : Failure.INSUFFICIENT_COST;
    }

    public enum Failure {
        SERVER_ONLY, EMPTY, NO_QUALITY, NO_CHAIN, AMBIGUOUS_CHAIN, NOT_MEMBER, AT_TOP, NO_STEP, DISABLED,
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
