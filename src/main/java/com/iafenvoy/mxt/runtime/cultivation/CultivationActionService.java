package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.AuraChunkData;
import com.iafenvoy.mxt.attachment.ResourceHolderData;
import com.iafenvoy.mxt.attachment.SpiritData;
import com.iafenvoy.mxt.data.cultivation.CultivateActionDefinition;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.runtime.behavior.BehaviorContext;
import com.iafenvoy.mxt.runtime.behavior.BehaviorContext.Kind;
import com.iafenvoy.mxt.runtime.behavior.DomainBehaviorService;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Evaluation;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Authoritative lifecycle for the one cultivation action an entity may run at a time.
 */
public final class CultivationActionService {
    private CultivationActionService() {
    }

    public static Result start(SpiritData spirit, @NotNull Identifier actionId, CultivateActionDefinition definition,
                               long gameTime, BooleanSupplier conditionsMet) {
        if (spirit.cultivateAction().isPresent()) return Result.rejected(Failure.ALREADY_ACTIVE, null);
        if (spirit.isCultivateActionOnCooldown(actionId, gameTime)) return Result.rejected(Failure.COOLDOWN, null);
        if (!conditionsMet.getAsBoolean()) return Result.rejected(Failure.CONDITIONS, null);
        spirit.startCultivateAction(actionId, gameTime, gameTime);
        return Result.startedResult();
    }

    /**
     * Entity-aware entry point which evaluates every data-defined start condition in the fixed condition registry.
     */
    public static Result start(LivingEntity entity, SpiritData spirit, Identifier actionId, CultivateActionDefinition definition,
                               long gameTime, FormulaContext context) {
        boolean conditions = definition.startConditions().stream().allMatch(id -> MxtTypeRegistries.CULTIVATION_CONDITION.get(id)
                .map(reference -> reference.value().test(entity, context)).orElse(false));
        return start(spirit, actionId, definition, gameTime, () -> conditions);
    }

    /**
     * Resolves one due cultivation interval. All resource and aura requirements are checked before mutation.
     */
    public static Result tick(SpiritData spirit, ResourceHolderData resources, AuraChunkData aura, Identifier actionId,
                              CultivateActionDefinition definition, long gameTime, FormulaContext context,
                              BooleanSupplier conditionsMet) {
        return tick(spirit, resources, aura, actionId, definition, gameTime, context, conditionsMet, 1.0D);
    }

    /**
     * Entity-aware tick path that applies only spirit-root and technique cultivation modifiers.
     */
    public static Result tick(LivingEntity entity, SpiritData spirit, ResourceHolderData resources, AuraChunkData aura, Identifier actionId,
                              CultivateActionDefinition definition, long gameTime, FormulaContext context,
                              BooleanSupplier conditionsMet) {
        double affinity = CultivationAffinity.multiplier(spirit, aura, context, id -> MxtDatapackRegistries.get(MxtDatapackRegistries.SPIRIT_ROOT, id),
                id -> MxtDatapackRegistries.get(MxtDatapackRegistries.CULTIVATION_TECHNIQUE, id));
        return tick(spirit, resources, aura, actionId, definition, gameTime, context, conditionsMet, affinity);
    }

    private static Result tick(SpiritData spirit, ResourceHolderData resources, AuraChunkData aura, Identifier actionId,
                               CultivateActionDefinition definition, long gameTime, FormulaContext context,
                               BooleanSupplier conditionsMet, double affinity) {
        if (spirit.cultivateAction().filter(actionId::equals).isEmpty())
            return Result.rejected(Failure.NOT_ACTIVE, null);
        if (!conditionsMet.getAsBoolean()) return stop(spirit, actionId, definition, gameTime, Failure.CONDITIONS);
        if (!aura.hasEnvironmentTags(definition.environmentTags())) return Result.rejected(Failure.ENVIRONMENT, null);
        if (gameTime < spirit.nextCultivateTick()) return Result.waitingResult();
        double gain = definition.progressGain().evaluate(context) * affinity;
        double auraCost = definition.auraCost().evaluate(context);
        if (!Double.isFinite(affinity) || affinity < 0.0D || !Double.isFinite(gain) || gain < 0.0D || !Double.isFinite(auraCost) || auraCost < 0.0D)
            return stop(spirit, actionId, definition, gameTime, Failure.INVALID_FORMULA);
        Evaluation costs;
        try {
            costs = ResourceTransactions.evaluate(definition.costs(), context);
        } catch (IllegalArgumentException | IllegalStateException error) {
            return stop(spirit, actionId, definition, gameTime, Failure.INVALID_FORMULA);
        }
        ResourceTransactions.Result preview = ResourceTransactions.tryConsume(copyOf(resources), costs);
        if (!preview.committed()) return Result.rejected(Failure.INSUFFICIENT_RESOURCE, preview.failedResource());
        if (aura.concentration() < auraCost) return Result.rejected(Failure.INSUFFICIENT_AURA, null);
        ResourceTransactions.Result payment = ResourceTransactions.tryConsume(resources, costs);
        if (!payment.committed()) return Result.rejected(Failure.INSUFFICIENT_RESOURCE, payment.failedResource());
        aura.setConcentration(aura.concentration() - auraCost);
        spirit.setCultivationProgress(spirit.cultivationProgress() + gain);
        spirit.scheduleCultivateTick(Math.addExact(gameTime, definition.tickInterval()));
        DomainBehaviorService.execute(MxtTypeRegistries.CULTIVATION_OUTCOME_BEHAVIOR, definition.gainBehavior(), BehaviorContext.of(
                Kind.CULTIVATION_GAIN, actionId, null, context, true));
        return Result.progressed(gain, payment.amounts());
    }

    public static Result stop(SpiritData spirit, Identifier actionId, CultivateActionDefinition definition, long gameTime) {
        return stop(spirit, actionId, definition, gameTime, null);
    }

    private static Result stop(SpiritData spirit, Identifier actionId, CultivateActionDefinition definition, long gameTime, Failure reason) {
        spirit.stopCultivateAction(actionId, Math.addExact(gameTime, definition.cooldownTicks()));
        return reason == null ? Result.stoppedResult() : Result.rejected(reason, null);
    }

    private static ResourceHolderData copyOf(ResourceHolderData source) {
        ResourceHolderData copy = new ResourceHolderData();
        source.values().forEach(copy::set);
        return copy;
    }

    public enum Failure {DISABLED, ALREADY_ACTIVE, COOLDOWN, CONDITIONS, NOT_ACTIVE, ENVIRONMENT, INVALID_FORMULA, INSUFFICIENT_RESOURCE, INSUFFICIENT_AURA}

    public record Result(boolean started, boolean progressed, boolean waiting, boolean stopped, Failure failure,
                         Identifier failedResource,
                         double gainedProgress, Map<Identifier, Double> paidCosts) {
        private static Result startedResult() {
            return new Result(true, false, false, false, null, null, 0.0D, Map.of());
        }

        private static Result progressed(double gained, Map<Identifier, Double> costs) {
            return new Result(false, true, false, false, null, null, gained, costs);
        }

        private static Result waitingResult() {
            return new Result(false, false, true, false, null, null, 0.0D, Map.of());
        }

        private static Result stoppedResult() {
            return new Result(false, false, false, true, null, null, 0.0D, Map.of());
        }

        private static Result rejected(Failure failure, Identifier resource) {
            return new Result(false, false, false, false, failure, resource, 0.0D, Map.of());
        }
    }
}
