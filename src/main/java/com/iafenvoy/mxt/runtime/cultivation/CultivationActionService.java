package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.AuraChunkData;
import com.iafenvoy.mxt.attachment.ResourceHolderData;
import com.iafenvoy.mxt.attachment.SpiritData;
import com.iafenvoy.mxt.data.cultivation.CultivateAction;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.resource.ResourceGain;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Evaluation;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.CollectionHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.function.BooleanSupplier;

/**
 * Authoritative lifecycle for the one cultivation action an entity may run at a time.
 */
public final class CultivationActionService {
    private CultivationActionService() {
    }

    public static Result start(SpiritData spirit, @NotNull Identifier actionId, CultivateAction definition,
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
    public static Result start(LivingEntity entity, SpiritData spirit, Identifier actionId, CultivateAction definition,
                               long gameTime, FormulaContext context) {
        boolean conditions = definition.startCondition().test(entity, context);
        return start(spirit, actionId, definition, gameTime, () -> conditions);
    }

    /**
     * Resolves one due cultivation interval. All resource and aura requirements are checked before mutation.
     */
    public static Result tick(SpiritData spirit, ResourceHolderData resources, AuraChunkData aura, Identifier actionId,
                              CultivateAction definition, long gameTime, FormulaContext context,
                              BooleanSupplier conditionsMet) {
        return tick(spirit, resources, aura, actionId, definition, gameTime, context, conditionsMet, 1.0D);
    }

    /**
     * Entity-aware tick path that applies only spirit-root and technique cultivation modifiers.
     */
    public static Result tick(LivingEntity entity, SpiritData spirit, ResourceHolderData resources, AuraChunkData aura, Identifier actionId,
                              CultivateAction definition, long gameTime, FormulaContext context,
                              BooleanSupplier conditionsMet) {
        double affinity = CultivationAffinity.multiplier(spirit, aura, context, id -> MxtDatapackRegistries.get(MxtDatapackRegistries.SPIRIT_ROOT, id),
                id -> MxtDatapackRegistries.get(MxtDatapackRegistries.CULTIVATION_TECHNIQUE, id));
        return tick(spirit, resources, aura, actionId, definition, gameTime, context, conditionsMet, affinity);
    }

    /**
     * Authoritative environment-aware cultivation path.
     */
    public static Result tick(LivingEntity entity, SpiritData spirit, ResourceHolderData resources, AuraResult aura, Identifier actionId,
                              CultivateAction definition, long gameTime, FormulaContext context,
                              BooleanSupplier conditionsMet) {
        if (aura.suppressCultivate()) return stop(spirit, actionId, definition, gameTime, Failure.ENVIRONMENT);
        double affinity = CultivationAffinity.multiplier(spirit, aura, context, id -> MxtDatapackRegistries.get(MxtDatapackRegistries.SPIRIT_ROOT, id),
                id -> MxtDatapackRegistries.get(MxtDatapackRegistries.CULTIVATION_TECHNIQUE, id));
        return tick(entity, spirit, resources, aura, actionId, definition, gameTime, context, conditionsMet, affinity);
    }

    private static Result tick(LivingEntity entity, SpiritData spirit, ResourceHolderData resources, AuraResult aura, Identifier actionId,
                               CultivateAction definition, long gameTime, FormulaContext context,
                               BooleanSupplier conditionsMet, double affinity) {
        if (spirit.cultivateAction().filter(actionId::equals).isEmpty())
            return Result.rejected(Failure.NOT_ACTIVE, null);
        if (!conditionsMet.getAsBoolean()) return stop(spirit, actionId, definition, gameTime, Failure.CONDITIONS);
        if (!CollectionHelper.containsAllFast(aura.environmentTags(), definition.environmentTags()))
            return Result.rejected(Failure.ENVIRONMENT, null);
        if (gameTime < spirit.nextCultivateTick()) return Result.waitingResult();
        double gain = definition.progressGain().evaluate(context) * affinity;
        double auraCost = definition.auraCost().evaluate(context);
        if (!Double.isFinite(affinity) || affinity < 0.0D || !Double.isFinite(gain) || gain < 0.0D || !Double.isFinite(auraCost) || auraCost < 0.0D)
            return stop(spirit, actionId, definition, gameTime, Failure.INVALID_FORMULA);
        Evaluation costs;
        Map<Identifier, Double> gains;
        try {
            costs = ResourceTransactions.evaluate(entity, definition.costs(), context);
            gains = evaluateGains(definition.auraGains(), context);
        } catch (IllegalArgumentException | IllegalStateException error) {
            return stop(spirit, actionId, definition, gameTime, Failure.INVALID_FORMULA);
        }
        ResourceTransactions.Result preview = ResourceTransactions.tryConsume(copyOf(resources), costs);
        if (!preview.committed()) return Result.rejected(Failure.INSUFFICIENT_RESOURCE, preview.failedResource());
        if (!canApplyGains(entity, copyOf(resources), gains, context))
            return stop(spirit, actionId, definition, gameTime, Failure.INVALID_FORMULA);
        if (!AuraService.consume(entity.level(), entity.blockPosition(), auraCost))
            return Result.rejected(Failure.INSUFFICIENT_AURA, null);
        ResourceTransactions.Result payment = ResourceTransactions.tryConsume(resources, costs);
        if (!payment.committed()) return Result.rejected(Failure.INSUFFICIENT_RESOURCE, payment.failedResource());
        spirit.setCultivationProgress(spirit.cultivationProgress() + gain);
        applyGains(entity, resources, gains, context);
        restoreAbsorbedResource(entity, spirit, resources, gain, context);
        spirit.scheduleCultivateTick(Math.addExact(gameTime, definition.tickInterval()));
        definition.tickAction().execute(entity, context);
        return Result.progressed(gain, payment.amounts());
    }

    private static Result tick(SpiritData spirit, ResourceHolderData resources, AuraChunkData aura, Identifier actionId,
                               CultivateAction definition, long gameTime, FormulaContext context,
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
        Map<Identifier, Double> gains;
        try {
            costs = ResourceTransactions.evaluate(definition.costs(), context);
            gains = evaluateGains(definition.auraGains(), context);
        } catch (IllegalArgumentException | IllegalStateException error) {
            return stop(spirit, actionId, definition, gameTime, Failure.INVALID_FORMULA);
        }
        ResourceTransactions.Result preview = ResourceTransactions.tryConsume(copyOf(resources), costs);
        if (!preview.committed()) return Result.rejected(Failure.INSUFFICIENT_RESOURCE, preview.failedResource());
        if (!canApplyGains(copyOf(resources), gains, context))
            return stop(spirit, actionId, definition, gameTime, Failure.INVALID_FORMULA);
        if (aura.concentration() < auraCost) return Result.rejected(Failure.INSUFFICIENT_AURA, null);
        ResourceTransactions.Result payment = ResourceTransactions.tryConsume(resources, costs);
        if (!payment.committed()) return Result.rejected(Failure.INSUFFICIENT_RESOURCE, payment.failedResource());
        aura.setConcentration(aura.concentration() - auraCost);
        spirit.setCultivationProgress(spirit.cultivationProgress() + gain);
        applyGains(resources, gains, context);
        restoreAbsorbedResource(spirit, resources, gain, context);
        spirit.scheduleCultivateTick(Math.addExact(gameTime, definition.tickInterval()));
        return Result.progressed(gain, payment.amounts());
    }

    public static Result stop(SpiritData spirit, Identifier actionId, CultivateAction definition, long gameTime) {
        return stop(spirit, actionId, definition, gameTime, null);
    }

    private static Result stop(SpiritData spirit, Identifier actionId, CultivateAction definition, long gameTime, Failure reason) {
        spirit.stopCultivateAction(actionId, Math.addExact(gameTime, definition.cooldownTicks()));
        return reason == null ? Result.stoppedResult() : Result.rejected(reason, null);
    }

    private static ResourceHolderData copyOf(ResourceHolderData source) {
        ResourceHolderData copy = new ResourceHolderData();
        source.values().forEach(copy::set);
        return copy;
    }

    private static Map<Identifier, Double> evaluateGains(List<ResourceGain> entries, FormulaContext context) {
        Map<Identifier, Double> amounts = new LinkedHashMap<>();
        for (ResourceGain entry : entries) amounts.merge(entry.id(), entry.evaluate(context), Double::sum);
        return Map.copyOf(amounts);
    }

    private static boolean canApplyGains(ResourceHolderData resources, Map<Identifier, Double> gains, FormulaContext context) {
        for (Entry<Identifier, Double> gain : gains.entrySet()) {
            Resource definition = MxtDatapackRegistries.get(MxtDatapackRegistries.RESOURCE, gain.getKey()).orElse(null);
            if (definition == null || !ResourceService.change(resources, gain.getKey(), definition, gain.getValue(), context).valid())
                return false;
        }
        return true;
    }

    private static boolean canApplyGains(LivingEntity entity, ResourceHolderData resources, Map<Identifier, Double> gains,
                                         FormulaContext context) {
        for (Entry<Identifier, Double> gain : gains.entrySet()) {
            Resource definition = MxtDatapackRegistries.get(MxtDatapackRegistries.RESOURCE, gain.getKey()).orElse(null);
            if (definition == null || !ResourceService.change(resources, gain.getKey(), definition, gain.getValue(),
                    ResourceService.formulaContext(entity, gain.getKey(), definition, context)).valid())
                return false;
        }
        return true;
    }

    private static void applyGains(ResourceHolderData resources, Map<Identifier, Double> gains, FormulaContext context) {
        for (Entry<Identifier, Double> gain : gains.entrySet())
            MxtDatapackRegistries.get(MxtDatapackRegistries.RESOURCE, gain.getKey())
                    .ifPresent(definition -> ResourceService.change(resources, gain.getKey(), definition, gain.getValue(), context));
    }

    private static void applyGains(LivingEntity entity, ResourceHolderData resources, Map<Identifier, Double> gains,
                                   FormulaContext context) {
        for (Entry<Identifier, Double> gain : gains.entrySet())
            MxtDatapackRegistries.get(MxtDatapackRegistries.RESOURCE, gain.getKey())
                    .ifPresent(definition -> ResourceService.change(resources, gain.getKey(), definition, gain.getValue(),
                            ResourceService.formulaContext(entity, gain.getKey(), definition, context)));
    }

    /** Restores the current realm's resource from absorbed aura only when its datapack definition opts in. */
    private static void restoreAbsorbedResource(SpiritData spirit, ResourceHolderData resources, double absorbed,
                                                FormulaContext context) {
        activeResource(spirit).flatMap(id -> MxtDatapackRegistries.get(MxtDatapackRegistries.RESOURCE, id)
                .filter(Resource::restoreOnAbsorb).map(definition -> new ActiveResource(id, definition)))
                .ifPresent(active -> ResourceService.change(resources, active.id(), active.definition(), absorbed,
                        ResourceService.formulaContext(spirit, active.id(), active.definition(), context)));
    }

    private static void restoreAbsorbedResource(LivingEntity entity, SpiritData spirit, ResourceHolderData resources,
                                                double absorbed, FormulaContext context) {
        activeResource(spirit).flatMap(id -> MxtDatapackRegistries.get(MxtDatapackRegistries.RESOURCE, id)
                .filter(Resource::restoreOnAbsorb).map(definition -> new ActiveResource(id, definition)))
                .ifPresent(active -> ResourceService.change(resources, active.id(), active.definition(), absorbed,
                        ResourceService.formulaContext(entity, active.id(), active.definition(), context)));
    }

    private static java.util.Optional<Identifier> activeResource(SpiritData spirit) {
        return spirit.realmStage().flatMap(id -> MxtDatapackRegistries.get(MxtDatapackRegistries.REALM_STAGE, id))
                .map(realm -> HolderHelper.id(realm.resource()));
    }

    private record ActiveResource(Identifier id, Resource definition) {
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
