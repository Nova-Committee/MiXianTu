package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.ResourceHolderData;
import com.iafenvoy.mxt.attachment.SpiritData;
import com.iafenvoy.mxt.data.cultivation.RealmStageDefinition;
import com.iafenvoy.mxt.event.CultivationBreakEvent;
import com.iafenvoy.mxt.event.CultivationBreakEvent.Post;
import com.iafenvoy.mxt.event.CultivationBreakEvent.Pre;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtCriteriaTriggers;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.runtime.ability.AbilityEventBridge;
import com.iafenvoy.mxt.runtime.behavior.BehaviorContext;
import com.iafenvoy.mxt.runtime.behavior.BehaviorContext.Kind;
import com.iafenvoy.mxt.runtime.behavior.DomainBehaviorService;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Evaluation;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Result;
import com.iafenvoy.mxt.runtime.tribulation.TribulationService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Server-side breakthrough transaction. Content conditions are evaluated by callers before payment is committed.
 */
public final class CultivationService {
    private CultivationService() {
    }

    public static BreakthroughResult attempt(SpiritData spirit, ResourceHolderData resources, Identifier targetId,
                                             RealmStageDefinition target, FormulaContext context, BooleanSupplier conditionsMet) {
        return attempt(spirit, resources, targetId, target, context, conditionsMet, NeoForge.EVENT_BUS);
    }

    /**
     * Entity-aware breakthrough entry point. It preserves the same transaction semantics and
     * dispatches triggered abilities only after the realm state has committed.
     */
    public static BreakthroughResult attempt(LivingEntity entity, SpiritData spirit, ResourceHolderData resources, Identifier targetId,
                                             RealmStageDefinition target, FormulaContext context, BooleanSupplier conditionsMet) {
        boolean configuredConditions = target.breakthroughConditions().stream().allMatch(id -> MxtTypeRegistries.CULTIVATION_CONDITION.get(id)
                .map(condition -> condition.value().test(entity, context)).orElse(false));
        boolean requiredAbilities = target.abilityRequirements().stream()
                .allMatch(ability -> entity.getData(MxtAttachments.ABILITY_HOLDER).has(ability));
        BreakthroughResult result = attempt(spirit, resources, targetId, target, context, () -> configuredConditions && requiredAbilities && conditionsMet.getAsBoolean());
        if (result.advanced()) {
            if (entity instanceof ServerPlayer player) MxtCriteriaTriggers.BREAKTHROUGH.get().trigger(player, targetId);
            DomainBehaviorService.execute(MxtTypeRegistries.CULTIVATION_OUTCOME_BEHAVIOR, target.successBehavior(), BehaviorContext.of(
                    Kind.BREAKTHROUGH_SUCCESS, targetId, entity, context, true));
            target.tribulation().flatMap(id -> MxtDatapackRegistries.get(MxtDatapackRegistries.TRIBULATION, id)).ifPresent(tribulation ->
                    TribulationService.start(entity, entity.getData(MxtAttachments.TRIBULATION), target.tribulation().orElseThrow(), tribulation,
                            entity.level().getGameTime(), context));
            AbilityEventBridge.onBreakthrough(entity, targetId, context);
        } else {
            DomainBehaviorService.execute(MxtTypeRegistries.CULTIVATION_OUTCOME_BEHAVIOR, target.failBehavior(), BehaviorContext.of(
                    Kind.BREAKTHROUGH_FAILURE, targetId, entity, context, false));
        }
        return result;
    }

    /**
     * Variant for integrations that own a dedicated event bus.
     */
    public static BreakthroughResult attempt(SpiritData spirit, ResourceHolderData resources, @NotNull Identifier targetId,
                                             RealmStageDefinition target, FormulaContext context, BooleanSupplier conditionsMet, @NotNull IEventBus eventBus) {
        if (target.parent().isPresent() && !target.parent().equals(spirit.realmStage()))
            return BreakthroughResult.rejected(Failure.WRONG_PARENT, null);
        double threshold = target.progressThreshold().evaluate(context);
        if (!Double.isFinite(threshold) || threshold < 0.0D)
            return BreakthroughResult.rejected(Failure.INVALID_FORMULA, null);
        if (spirit.cultivationProgress() < threshold)
            return BreakthroughResult.rejected(Failure.INSUFFICIENT_PROGRESS, null);
        if (!conditionsMet.getAsBoolean()) return BreakthroughResult.rejected(Failure.CONDITIONS, null);
        Evaluation costs;
        try {
            costs = ResourceTransactions.evaluate(target.breakthroughCosts(), context);
        } catch (IllegalArgumentException exception) {
            return BreakthroughResult.rejected(Failure.INVALID_FORMULA, null);
        }
        Pre event = new Pre(spirit, resources, targetId, target, context, threshold, costs.amounts());
        if (eventBus.post(event).isCanceled()) return BreakthroughResult.rejected(Failure.CANCELLED, null);
        Result payment = ResourceTransactions.tryConsume(resources, new Evaluation(event.costs()));
        if (!payment.committed())
            return BreakthroughResult.rejected(Failure.INSUFFICIENT_RESOURCE, payment.failedResource());
        spirit.setRealmStage(targetId);
        spirit.setCultivationProgress(0.0D);
        eventBus.post(new Post(spirit, resources, targetId, target, context, threshold, payment.amounts()));
        return BreakthroughResult.committed(payment.amounts());
    }

    public enum Failure {DISABLED, WRONG_PARENT, INSUFFICIENT_PROGRESS, CONDITIONS, INSUFFICIENT_RESOURCE, INVALID_FORMULA, CANCELLED, SERVER_ONLY}

    public record BreakthroughResult(boolean advanced, Failure failure, Identifier failedResource,
                                     Map<Identifier, Double> costs) {
        private static BreakthroughResult committed(Map<Identifier, Double> costs) {
            return new BreakthroughResult(true, null, null, costs);
        }

        private static BreakthroughResult rejected(Failure failure, Identifier resource) {
            return new BreakthroughResult(false, failure, resource, Map.of());
        }
    }
}
