package com.iafenvoy.mxt.runtime.cultivation;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.attachment.AuraChunkData;
import com.iafenvoy.mxt.attachment.ResourceHolderData;
import com.iafenvoy.mxt.attachment.SpiritData;
import com.iafenvoy.mxt.data.cultivation.CultivateAction;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.resource.ResourceGain;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.resource.ResourceService.Bounds;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Evaluation;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.CollectionHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Authoritative lifecycle for the one cultivation action an entity may run at a time.
 */
public final class CultivationActionService {
    private CultivationActionService() {
    }

    public static Result start(SpiritData spirit, @NotNull Identifier actionId, CultivateAction definition,
                               long gameTime, BooleanSupplier conditionsMet) {
        Holder<CultivateAction> action = MxtDatapackRegistries.holder(MxtResourceKeys.CULTIVATE_ACTION, actionId).orElse(null);
        if (action == null) return Result.rejected(Failure.DISABLED, null);
        return start(spirit, action, definition, gameTime, conditionsMet);
    }

    public static Result start(SpiritData spirit, @NotNull Holder<CultivateAction> action, CultivateAction definition,
                               long gameTime, BooleanSupplier conditionsMet) {
        if (spirit.cultivateAction().isPresent()) return Result.rejected(Failure.ALREADY_ACTIVE, null);
        if (spirit.isCultivateActionOnCooldown(action, gameTime)) return Result.rejected(Failure.COOLDOWN, null);
        if (!conditionsMet.getAsBoolean()) return Result.rejected(Failure.CONDITIONS, null);
        spirit.startCultivateAction(action, gameTime, gameTime);
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
        double affinity = CultivationAffinity.multiplier(spirit, aura, context, id -> MxtDatapackRegistries.get(MxtResourceKeys.SPIRIT_ROOT, id),
                id -> MxtDatapackRegistries.get(MxtResourceKeys.CULTIVATION_TECHNIQUE, id));
        return tick(spirit, resources, aura, actionId, definition, gameTime, context, conditionsMet, affinity);
    }

    /**
     * Authoritative environment-aware cultivation path.
     */
    public static Result tick(LivingEntity entity, SpiritData spirit, ResourceHolderData resources, AuraResult aura, Identifier actionId,
                              CultivateAction definition, long gameTime, FormulaContext context,
                              BooleanSupplier conditionsMet) {
        Holder<CultivateAction> action = MxtDatapackRegistries.holder(MxtResourceKeys.CULTIVATE_ACTION, actionId).orElse(null);
        if (action == null) return Result.rejected(Failure.DISABLED, null);
        return tick(entity, spirit, resources, aura, action, definition, gameTime, context, conditionsMet);
    }

    public static Result tick(LivingEntity entity, SpiritData spirit, ResourceHolderData resources, AuraResult aura, Holder<CultivateAction> action,
                              CultivateAction definition, long gameTime, FormulaContext context, BooleanSupplier conditionsMet) {
        Identifier actionId = HolderHelper.id(action);
        if (aura.suppressCultivate()) return stop(spirit, action, definition, gameTime, Failure.ENVIRONMENT);
        double affinity = CultivationAffinity.multiplier(spirit, aura, context, id -> MxtDatapackRegistries.get(MxtResourceKeys.SPIRIT_ROOT, id),
                id -> MxtDatapackRegistries.get(MxtResourceKeys.CULTIVATION_TECHNIQUE, id));
        return tick(entity, spirit, resources, aura, action, definition, gameTime, context, conditionsMet, affinity);
    }

    private static Result tick(LivingEntity entity, SpiritData spirit, ResourceHolderData resources, AuraResult aura, Holder<CultivateAction> action,
                               CultivateAction definition, long gameTime, FormulaContext context,
                               BooleanSupplier conditionsMet, double affinity) {
        if (spirit.cultivateAction().filter(action::equals).isEmpty())
            return Result.rejected(Failure.NOT_ACTIVE, null);
        if (!conditionsMet.getAsBoolean()) return stop(spirit, action, definition, gameTime, Failure.CONDITIONS);
        if (!CollectionHelper.containsAllFast(aura.auraKinds(), definition.auraKinds()))
            return Result.rejected(Failure.ENVIRONMENT, null);
        ItemAuraService.tick(entity, spirit, context);
        if (gameTime < spirit.nextCultivateTick()) {
            convert(activeResource(spirit), spirit, resources, context, entity);
            return Result.waitingResult();
        }
        double gain = definition.absorbAmount().evaluate(context) * affinity;
        double auraCost = definition.auraCost().evaluate(context);
        if (!Double.isFinite(affinity) || affinity < 0.0D || !Double.isFinite(gain) || gain < 0.0D || !Double.isFinite(auraCost) || auraCost < 0.0D)
            return stop(spirit, action, definition, gameTime, Failure.INVALID_FORMULA);
        Evaluation costs;
        Map<Identifier, Double> gains;
        try {
            costs = ResourceTransactions.evaluate(entity, definition.costs(), context);
            gains = evaluateGains(definition.auraGains(), context);
        } catch (IllegalArgumentException | IllegalStateException error) {
            return stop(spirit, action, definition, gameTime, Failure.INVALID_FORMULA);
        }
        ResourceTransactions.Result preview = ResourceTransactions.tryConsume(copyOf(resources), costs);
        if (!preview.committed()) return Result.rejected(Failure.INSUFFICIENT_RESOURCE, preview.failedResource());
        if (!canApplyGains(entity, copyOf(resources), gains, context) || !canConvertAbsorption(entity, spirit, resources, gain, context))
            return stop(spirit, action, definition, gameTime, Failure.INVALID_FORMULA);
        if (!AuraService.consume(entity.level(), entity.blockPosition(), auraCost))
            return Result.rejected(Failure.INSUFFICIENT_AURA, null);
        ResourceTransactions.Result payment = ResourceTransactions.tryConsume(resources, costs);
        if (!payment.committed()) return Result.rejected(Failure.INSUFFICIENT_RESOURCE, payment.failedResource());
        restoreAbsorption(entity, spirit, resources, gain, context);
        convert(activeResource(spirit), spirit, resources, context, entity);
        applyGains(entity, resources, gains, context);
        spirit.scheduleCultivateTick(Math.addExact(gameTime, definition.tickInterval()));
        definition.tickAction().execute(entity, context);
        return Result.progressed(gain, payment.amounts());
    }

    private static Result tick(SpiritData spirit, ResourceHolderData resources, AuraChunkData aura, Identifier actionId,
                               CultivateAction definition, long gameTime, FormulaContext context,
                               BooleanSupplier conditionsMet, double affinity) {
        Holder<CultivateAction> action = MxtDatapackRegistries.holder(MxtResourceKeys.CULTIVATE_ACTION, actionId).orElse(null);
        if (action == null) return Result.rejected(Failure.DISABLED, null);
        if (spirit.cultivateAction().filter(action::equals).isEmpty())
            return Result.rejected(Failure.NOT_ACTIVE, null);
        if (!conditionsMet.getAsBoolean()) return stop(spirit, actionId, definition, gameTime, Failure.CONDITIONS);
        if (!aura.hasAuraKinds(definition.auraKinds())) return Result.rejected(Failure.ENVIRONMENT, null);
        if (gameTime < spirit.nextCultivateTick()) {
            convert(activeResource(spirit), spirit, resources, context);
            return Result.waitingResult();
        }
        double gain = definition.absorbAmount().evaluate(context) * affinity;
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
        if (!canApplyGains(copyOf(resources), gains, context) || !canConvertAbsorption(spirit, resources, gain, context))
            return stop(spirit, actionId, definition, gameTime, Failure.INVALID_FORMULA);
        if (aura.concentration() < auraCost) return Result.rejected(Failure.INSUFFICIENT_AURA, null);
        ResourceTransactions.Result payment = ResourceTransactions.tryConsume(resources, costs);
        if (!payment.committed()) return Result.rejected(Failure.INSUFFICIENT_RESOURCE, payment.failedResource());
        aura.setConcentration(aura.concentration() - auraCost);
        restoreAbsorption(spirit, resources, gain, context);
        convert(activeResource(spirit), spirit, resources, context);
        applyGains(resources, gains, context);
        spirit.scheduleCultivateTick(Math.addExact(gameTime, definition.tickInterval()));
        return Result.progressed(gain, payment.amounts());
    }

    public static Result stop(SpiritData spirit, Identifier actionId, CultivateAction definition, long gameTime) {
        return stop(spirit, actionId, definition, gameTime, null);
    }

    private static Result stop(SpiritData spirit, Identifier actionId, CultivateAction definition, long gameTime, Failure reason) {
        Holder<CultivateAction> action = MxtDatapackRegistries.holder(MxtResourceKeys.CULTIVATE_ACTION, actionId).orElse(null);
        return action == null ? Result.rejected(Failure.DISABLED, null) : stop(spirit, action, definition, gameTime, reason);
    }

    private static Result stop(SpiritData spirit, Holder<CultivateAction> action, CultivateAction definition, long gameTime, Failure reason) {
        spirit.stopCultivateAction(action, Math.addExact(gameTime, definition.cooldownTicks()));
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
        return amounts;
    }

    private static boolean canApplyGains(ResourceHolderData resources, Map<Identifier, Double> gains, FormulaContext context) {
        for (Entry<Identifier, Double> gain : gains.entrySet()) {
            Resource definition = MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, gain.getKey()).orElse(null);
            if (definition == null || !ResourceService.change(resources, gain.getKey(), definition, gain.getValue(), context).valid())
                return false;
        }
        return true;
    }

    private static boolean canApplyGains(LivingEntity entity, ResourceHolderData resources, Map<Identifier, Double> gains,
                                         FormulaContext context) {
        for (Entry<Identifier, Double> gain : gains.entrySet()) {
            Resource definition = MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, gain.getKey()).orElse(null);
            if (definition == null || !ResourceService.change(resources, gain.getKey(), definition, gain.getValue(),
                    ResourceService.formulaContext(entity, gain.getKey(), definition, context)).valid())
                return false;
        }
        return true;
    }

    private static void applyGains(ResourceHolderData resources, Map<Identifier, Double> gains, FormulaContext context) {
        for (Entry<Identifier, Double> gain : gains.entrySet())
            MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, gain.getKey())
                    .ifPresent(definition -> ResourceService.change(resources, gain.getKey(), definition, gain.getValue(), context));
    }

    private static void applyGains(LivingEntity entity, ResourceHolderData resources, Map<Identifier, Double> gains,
                                   FormulaContext context) {
        for (Entry<Identifier, Double> gain : gains.entrySet())
            MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, gain.getKey())
                    .ifPresent(definition -> ResourceService.change(resources, gain.getKey(), definition, gain.getValue(),
                            ResourceService.formulaContext(entity, gain.getKey(), definition, context)));
    }

    private static boolean canConvertAbsorption(SpiritData spirit, ResourceHolderData resources, double absorbed,
                                                FormulaContext context) {
        ActiveResource active = activeResource(spirit).flatMap(id -> MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, id)
                .map(definition -> new ActiveResource(id, definition))).orElse(null);
        return active == null || conversion(active, context, spirit, resources).valid();
    }

    private static boolean canConvertAbsorption(LivingEntity entity, SpiritData spirit, ResourceHolderData resources,
                                                double absorbed, FormulaContext context) {
        ActiveResource active = activeResource(spirit).flatMap(id -> MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, id)
                .map(definition -> new ActiveResource(id, definition))).orElse(null);
        return active == null || conversion(active, ResourceService.formulaContext(entity, active.id(), active.definition(), context), spirit, resources).valid();
    }

    /**
     * Absorption always restores the active resource first. Each conversion direction
     * has an independent source-side per-tick limit configured on the resource.
     */
    private static void restoreAbsorption(SpiritData spirit, ResourceHolderData resources, double absorbed,
                                          FormulaContext context) {
        activeResource(spirit).flatMap(id -> MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, id)
                .map(definition -> new ActiveResource(id, definition))).ifPresent(active ->
                ResourceService.change(resources, active.id(), active.definition(), absorbed,
                        ResourceService.formulaContext(spirit, active.id(), active.definition(), context)));
    }

    private static void restoreAbsorption(LivingEntity entity, SpiritData spirit, ResourceHolderData resources,
                                          double absorbed, FormulaContext context) {
        activeResource(spirit).flatMap(id -> MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, id)
                .map(definition -> new ActiveResource(id, definition))).ifPresent(active ->
                ResourceService.change(resources, active.id(), active.definition(), absorbed,
                        ResourceService.formulaContext(entity, active.id(), active.definition(), context)));
    }

    private static Conversion conversion(ActiveResource active, FormulaContext context, SpiritData spirit,
                                         ResourceHolderData resources) {
        double cultivationToResource = active.definition().cultivationToResource().multiplier().evaluate(context);
        double cultivationToResourceMaxPerTick = active.definition().cultivationToResource().maxPerTick().evaluate(context);
        double resourceToCultivation = active.definition().resourceToCultivation().multiplier().evaluate(context);
        double resourceToCultivationMaxPerTick = active.definition().resourceToCultivation().maxPerTick().evaluate(context);
        return Double.isFinite(cultivationToResource) && cultivationToResource >= 0.0D
                && Double.isFinite(cultivationToResourceMaxPerTick) && cultivationToResourceMaxPerTick >= 0.0D
                && Double.isFinite(resourceToCultivation) && resourceToCultivation >= 0.0D
                && Double.isFinite(resourceToCultivationMaxPerTick) && resourceToCultivationMaxPerTick >= 0.0D
                && ResourceService.resolveBounds(active.definition(), context).isPresent()
                ? new Conversion(cultivationToResource, cultivationToResourceMaxPerTick,
                resourceToCultivation, resourceToCultivationMaxPerTick) : Conversion.INVALID;
    }

    private static void convert(Optional<Identifier> resourceId, SpiritData spirit, ResourceHolderData resources,
                                FormulaContext context) {
        resourceId.flatMap(id -> MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, id)
                .map(definition -> new ActiveResource(id, definition))).ifPresent(active ->
                convert(active, spirit, resources, ResourceService.formulaContext(spirit, active.id(), active.definition(), context)));
    }

    private static void convert(Optional<Identifier> resourceId, SpiritData spirit, ResourceHolderData resources,
                                FormulaContext context, LivingEntity entity) {
        resourceId.flatMap(id -> MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, id)
                .map(definition -> new ActiveResource(id, definition))).ifPresent(active ->
                convert(active, spirit, resources, ResourceService.formulaContext(entity, active.id(), active.definition(), context)));
    }

    private static void convert(ActiveResource active, SpiritData spirit, ResourceHolderData resources,
                                FormulaContext context) {
        Conversion conversion = conversion(active, context, spirit, resources);
        if (!conversion.valid()) return;
        Resource definition = active.definition();
        Holder<Resource> resource = MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, active.id()).orElse(null);
        if (resource == null) return;
        Bounds bounds = ResourceService.resolveBounds(definition, context).orElseThrow();

        // While cultivating, stored resource is the preferred source for cultivation progress.
        // If it is depleted, the reverse conversion can restore a usable resource amount.
        if (conversion.resourceToCultivation() > 0.0D) {
            double available = Math.max(0.0D, resources.get(resource) - bounds.min());
            double consumed = Math.min(conversion.resourceToCultivationMaxPerTick(), available);
            if (consumed > 0.0D) {
                ResourceService.change(resources, active.id(), definition, -consumed, context);
                spirit.setCultivationProgress(spirit.cultivationProgress() + consumed * conversion.resourceToCultivation());
                return;
            }
        }

        if (conversion.cultivationToResource() > 0.0D) {
            double capacity = Math.max(0.0D, bounds.max() - resources.get(resource));
            double extracted = Math.min(conversion.cultivationToResourceMaxPerTick(),
                    Math.min(spirit.cultivationProgress(), capacity / conversion.cultivationToResource()));
            if (extracted > 0.0D) {
                spirit.setCultivationProgress(spirit.cultivationProgress() - extracted);
                ResourceService.change(resources, active.id(), definition, extracted * conversion.cultivationToResource(), context);
            }
        }
    }

    private static Optional<Identifier> activeResource(SpiritData spirit) {
        return spirit.realmStage().flatMap(id -> MxtDatapackRegistries.get(MxtResourceKeys.REALM_STAGE, id))
                .map(realm -> HolderHelper.id(realm.resource()));
    }

    private record ActiveResource(Identifier id, Resource definition) {
    }

    private record Conversion(double cultivationToResource, double cultivationToResourceMaxPerTick,
                              double resourceToCultivation, double resourceToCultivationMaxPerTick) {
        private static final Conversion INVALID = new Conversion(Double.NaN, Double.NaN, Double.NaN, Double.NaN);

        private boolean valid() {
            return Double.isFinite(this.cultivationToResource);
        }
    }

    public enum Failure {DISABLED, ALREADY_ACTIVE, COOLDOWN, CONDITIONS, NOT_ACTIVE, ENVIRONMENT, INVALID_FORMULA, INSUFFICIENT_RESOURCE, INSUFFICIENT_AURA}

    public record Result(boolean started, boolean progressed, boolean waiting, boolean stopped, Failure failure,
                         Identifier failedResource,
                         double absorbedAmount, Map<Identifier, Double> paidCosts) {
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
