package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.attachment.AuraChunkComponent;
import com.iafenvoy.mxt.attachment.ResourceHolderComponent;
import com.iafenvoy.mxt.attachment.SpiritComponent;
import com.iafenvoy.mxt.data.cultivation.CultivateAction;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.resource.ResourceGain;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.resource.ResourceService.Bounds;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Evaluation;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.CollectionHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
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

    public static Result start(SpiritComponent spirit, @NotNull Identifier actionId, CultivateAction definition,
                               long gameTime, BooleanSupplier conditionsMet) {
        Holder<CultivateAction> action = MxtDatapackRegistries.holder(MxtResourceKeys.CULTIVATE_ACTION, actionId).orElse(null);
        if (action == null) return Result.rejected(Failure.DISABLED, null);
        return start(spirit, action, definition, gameTime, conditionsMet);
    }

    public static Result start(SpiritComponent spirit, @NotNull Holder<CultivateAction> action, CultivateAction definition,
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
    public static Result start(LivingEntity entity, SpiritComponent spirit, Identifier actionId, CultivateAction definition,
                               long gameTime, FormulaContext context) {
        boolean conditions = definition.startCondition().test(entity, context);
        return start(spirit, actionId, definition, gameTime, () -> conditions);
    }

    /**
     * Resolves one due cultivation interval. All resource and aura requirements are checked before mutation.
     */
    public static Result tick(SpiritComponent spirit, ResourceHolderComponent resources, AuraChunkComponent aura, Identifier actionId,
                              CultivateAction definition, long gameTime, FormulaContext context,
                              BooleanSupplier conditionsMet) {
        return tick(spirit, resources, aura, actionId, definition, gameTime, context, conditionsMet, 1.0D);
    }

    /**
     * Entity-aware tick path that applies only spirit-root and technique cultivation modifiers.
     */
    public static Result tick(LivingEntity entity, SpiritComponent spirit, ResourceHolderComponent resources, AuraChunkComponent aura, Identifier actionId,
                              CultivateAction definition, long gameTime, FormulaContext context,
                              BooleanSupplier conditionsMet) {
        double affinity = CultivationAffinity.multiplier(spirit, aura, context, id -> MxtDatapackRegistries.get(MxtResourceKeys.SPIRIT_ROOT, id),
                id -> MxtDatapackRegistries.get(MxtResourceKeys.CULTIVATION_TECHNIQUE, id));
        return tick(spirit, resources, aura, actionId, definition, gameTime, context, conditionsMet, affinity);
    }

    /**
     * Authoritative environment-aware cultivation path.
     */
    public static Result tick(LivingEntity entity, SpiritComponent spirit, ResourceHolderComponent resources, AuraResult aura, Identifier actionId,
                              CultivateAction definition, long gameTime, FormulaContext context,
                              BooleanSupplier conditionsMet) {
        Holder<CultivateAction> action = MxtDatapackRegistries.holder(MxtResourceKeys.CULTIVATE_ACTION, actionId).orElse(null);
        if (action == null) return Result.rejected(Failure.DISABLED, null);
        return tick(entity, spirit, resources, aura, action, definition, gameTime, context, conditionsMet);
    }

    public static Result tick(LivingEntity entity, SpiritComponent spirit, ResourceHolderComponent resources, AuraResult aura, Holder<CultivateAction> action,
                              CultivateAction definition, long gameTime, FormulaContext context, BooleanSupplier conditionsMet) {
        Identifier actionId = HolderHelper.id(action);
        if (aura.suppressCultivate() || !aura.cultivateCondition().test(entity, context) || !realmCultivateCondition(spirit, entity, context))
            return stop(entity, spirit, action, definition, gameTime, Failure.ENVIRONMENT);
        double affinity = CultivationAffinity.multiplier(spirit, aura, context, id -> MxtDatapackRegistries.get(MxtResourceKeys.SPIRIT_ROOT, id),
                id -> MxtDatapackRegistries.get(MxtResourceKeys.CULTIVATION_TECHNIQUE, id));
        return tick(entity, spirit, resources, aura, action, definition, gameTime, context, conditionsMet, affinity);
    }

    private static Result tick(LivingEntity entity, SpiritComponent spirit, ResourceHolderComponent resources, AuraResult aura, Holder<CultivateAction> action,
                               CultivateAction definition, long gameTime, FormulaContext context,
                               BooleanSupplier conditionsMet, double affinity) {
        if (spirit.cultivateAction().filter(action::equals).isEmpty())
            return Result.rejected(Failure.NOT_ACTIVE, null);
        if (!conditionsMet.getAsBoolean())
            return stop(entity, spirit, action, definition, gameTime, Failure.CONDITIONS);
        if (!CollectionHelper.containsAllFast(aura.auraKinds(), definition.auraKinds()))
            return Result.rejected(Failure.ENVIRONMENT, null);
        ItemAuraService.tick(entity, spirit, resources, context);
        if (gameTime < spirit.nextCultivateTick()) {
            convert(activeResource(spirit), spirit, resources, context, entity);
            return Result.waitingResult();
        }
        double baseGain = definition.absorbAmount().evaluate(context) * affinity;
        Map<Holder<Resource>, Double> auraCosts = evaluateAuraCosts(definition, context);
        if (auraCosts == null) return stop(entity, spirit, action, definition, gameTime, Failure.INVALID_FORMULA);
        double auraCost = auraCosts.values().stream().mapToDouble(Double::doubleValue).sum();
        if (!Double.isFinite(affinity) || affinity < 0.0D || !Double.isFinite(baseGain) || baseGain < 0.0D)
            return stop(entity, spirit, action, definition, gameTime, Failure.INVALID_FORMULA);
        Evaluation costs;
        Map<Identifier, Double> gains;
        try {
            costs = ResourceTransactions.evaluate(entity, definition.costs(), context);
            gains = evaluateGains(definition.auraGains(), context);
        } catch (IllegalArgumentException | IllegalStateException error) {
            return stop(entity, spirit, action, definition, gameTime, Failure.INVALID_FORMULA);
        }
        double allocationFactor = 1.0D;
        if (auraCost > 0.0D && entity instanceof ServerPlayer player) {
            // The level prepass sets an upper bound from the shared chunk pool. Direct service
            // callers retain the normal full-cost behavior when no prepass exists.
            allocationFactor = AuraDistributionService.take(player).map(values -> allocationFactor(auraCosts, values)).orElse(1.0D);
            if (allocationFactor <= 0.0D) return Result.rejected(Failure.INSUFFICIENT_AURA, null);
        }
        double speed = aura.cultivationSpeed() * allocationFactor;
        double gain = baseGain * speed;
        gains.replaceAll((id, amount) -> amount * speed);
        if (!Double.isFinite(speed) || speed < 0.0D || !Double.isFinite(gain) || gain < 0.0D)
            return stop(entity, spirit, action, definition, gameTime, Failure.INVALID_FORMULA);
        ResourceTransactions.Result preview = ResourceTransactions.tryConsume(copyOf(resources), costs);
        if (!preview.committed()) return Result.rejected(Failure.INSUFFICIENT_RESOURCE, preview.failedResource());
        if (!canApplyGains(entity, copyOf(resources), gains, context) || !canConvertAbsorption(entity, spirit, resources, gain, context))
            return stop(entity, spirit, action, definition, gameTime, Failure.INVALID_FORMULA);
        if (!auraCosts.isEmpty() && !AuraService.consume(entity.level(), entity.blockPosition(), scaleAuraCosts(auraCosts, allocationFactor)))
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

    private static Result tick(SpiritComponent spirit, ResourceHolderComponent resources, AuraChunkComponent aura, Identifier actionId,
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
        Map<Holder<Resource>, Double> auraCosts = evaluateAuraCosts(definition, context);
        if (auraCosts == null) return stop(spirit, actionId, definition, gameTime, Failure.INVALID_FORMULA);
        double auraCost = auraCosts.values().stream().mapToDouble(Double::doubleValue).sum();
        if (!Double.isFinite(affinity) || affinity < 0.0D || !Double.isFinite(gain) || gain < 0.0D)
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
        if (!hasAura(aura, auraCosts)) return Result.rejected(Failure.INSUFFICIENT_AURA, null);
        ResourceTransactions.Result payment = ResourceTransactions.tryConsume(resources, costs);
        if (!payment.committed()) return Result.rejected(Failure.INSUFFICIENT_RESOURCE, payment.failedResource());
        aura.consume(auraCosts);
        restoreAbsorption(spirit, resources, gain, context);
        convert(activeResource(spirit), spirit, resources, context);
        applyGains(resources, gains, context);
        spirit.scheduleCultivateTick(Math.addExact(gameTime, definition.tickInterval()));
        return Result.progressed(gain, payment.amounts());
    }

    public static Result stop(SpiritComponent spirit, Identifier actionId, CultivateAction definition, long gameTime) {
        return stop(spirit, actionId, definition, gameTime, null);
    }

    public static Result stop(LivingEntity entity, SpiritComponent spirit, Identifier actionId, CultivateAction definition, long gameTime) {
        Holder<CultivateAction> action = MxtDatapackRegistries.holder(MxtResourceKeys.CULTIVATE_ACTION, actionId).orElse(null);
        return action == null ? Result.rejected(Failure.DISABLED, null) : stop(entity, spirit, action, definition, gameTime, null);
    }

    private static Result stop(SpiritComponent spirit, Identifier actionId, CultivateAction definition, long gameTime, Failure reason) {
        Holder<CultivateAction> action = MxtDatapackRegistries.holder(MxtResourceKeys.CULTIVATE_ACTION, actionId).orElse(null);
        return action == null ? Result.rejected(Failure.DISABLED, null) : stop(spirit, action, definition, gameTime, reason);
    }

    private static Result stop(SpiritComponent spirit, Holder<CultivateAction> action, CultivateAction definition, long gameTime, Failure reason) {
        spirit.stopCultivateAction(action, Math.addExact(gameTime, definition.cooldownTicks()));
        return reason == null ? Result.stoppedResult() : Result.rejected(reason, null);
    }

    private static Result stop(LivingEntity entity, SpiritComponent spirit, Holder<CultivateAction> action, CultivateAction definition,
                               long gameTime, Failure reason) {
        ItemAuraService.returnFloatingItem(entity);
        return stop(spirit, action, definition, gameTime, reason);
    }

    private static ResourceHolderComponent copyOf(ResourceHolderComponent source) {
        ResourceHolderComponent copy = new ResourceHolderComponent();
        source.values().forEach(copy::set);
        return copy;
    }

    private static Map<Identifier, Double> evaluateGains(List<ResourceGain> entries, FormulaContext context) {
        Map<Identifier, Double> amounts = new LinkedHashMap<>();
        for (ResourceGain entry : entries) amounts.merge(entry.id(), entry.evaluate(context), Double::sum);
        return amounts;
    }

    private static Map<Holder<Resource>, Double> evaluateAuraCosts(CultivateAction definition,
                                                                  FormulaContext context) {
        Map<Holder<Resource>, Double> values = new LinkedHashMap<>();
        for (Entry<Holder<Resource>, NumberProvider> entry : definition.auraCosts().entrySet()) {
            double value = entry.getValue().evaluate(context);
            if (!Double.isFinite(value) || value < 0.0D) return null;
            if (value > 0.0D) values.put(entry.getKey(), value);
        }
        return values;
    }

    private static boolean hasAura(AuraChunkComponent aura, Map<Holder<Resource>, Double> costs) {
        return costs.entrySet().stream().allMatch(entry -> aura.auras().getOrDefault(entry.getKey(), new AuraPool(0.0D, 0.0D, 0.0D)).amount() >= entry.getValue());
    }

    private static Map<Holder<Resource>, Double> scaleAuraCosts(Map<Holder<Resource>, Double> values,
                                                               double multiplier) {
        Map<Holder<Resource>, Double> result = new LinkedHashMap<>();
        values.forEach((element, amount) -> result.put(element, amount * multiplier));
        return result;
    }

    /**
     * A multi-element cultivation tick proceeds at the limiting element's allocated ratio.
     */
    private static double allocationFactor(Map<Holder<Resource>, Double> requested,
                                           Map<Holder<Resource>, Double> allocated) {
        return requested.entrySet().stream()
                .filter(entry -> entry.getValue() > 0.0D)
                .mapToDouble(entry -> Math.clamp(allocated.getOrDefault(entry.getKey(), 0.0D) / entry.getValue(), 0.0D, 1.0D))
                .min().orElse(1.0D);
    }

    private static boolean canApplyGains(ResourceHolderComponent resources, Map<Identifier, Double> gains, FormulaContext context) {
        for (Entry<Identifier, Double> gain : gains.entrySet()) {
            Resource definition = MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, gain.getKey()).orElse(null);
            if (definition == null || !ResourceService.change(resources, gain.getKey(), definition, gain.getValue(), context).valid())
                return false;
        }
        return true;
    }

    private static boolean canApplyGains(LivingEntity entity, ResourceHolderComponent resources, Map<Identifier, Double> gains,
                                         FormulaContext context) {
        for (Entry<Identifier, Double> gain : gains.entrySet()) {
            Resource definition = MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, gain.getKey()).orElse(null);
            if (definition == null || !ResourceService.change(resources, gain.getKey(), definition, gain.getValue(),
                    ResourceService.formulaContext(entity, gain.getKey(), definition, context)).valid())
                return false;
        }
        return true;
    }

    private static void applyGains(ResourceHolderComponent resources, Map<Identifier, Double> gains, FormulaContext context) {
        for (Entry<Identifier, Double> gain : gains.entrySet())
            MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, gain.getKey())
                    .ifPresent(definition -> ResourceService.change(resources, gain.getKey(), definition, gain.getValue(), context));
    }

    private static void applyGains(LivingEntity entity, ResourceHolderComponent resources, Map<Identifier, Double> gains,
                                   FormulaContext context) {
        for (Entry<Identifier, Double> gain : gains.entrySet())
            MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, gain.getKey())
                    .ifPresent(definition -> ResourceService.change(resources, gain.getKey(), definition, gain.getValue(),
                            ResourceService.formulaContext(entity, gain.getKey(), definition, context)));
    }

    private static boolean canConvertAbsorption(SpiritComponent spirit, ResourceHolderComponent resources, double absorbed,
                                                FormulaContext context) {
        ActiveResource active = activeResource(spirit).flatMap(id -> MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, id)
                .map(definition -> new ActiveResource(id, definition))).orElse(null);
        return active == null || conversion(active, context, spirit, resources).valid();
    }

    private static boolean canConvertAbsorption(LivingEntity entity, SpiritComponent spirit, ResourceHolderComponent resources,
                                                double absorbed, FormulaContext context) {
        ActiveResource active = activeResource(spirit).flatMap(id -> MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, id)
                .map(definition -> new ActiveResource(id, definition))).orElse(null);
        return active == null || conversion(active, ResourceService.formulaContext(entity, active.id(), active.definition(), context), spirit, resources).valid();
    }

    /**
     * Absorption always restores the active resource first. Each conversion direction
     * has an independent source-side per-tick limit configured on the resource.
     */
    private static void restoreAbsorption(SpiritComponent spirit, ResourceHolderComponent resources, double absorbed,
                                          FormulaContext context) {
        activeResource(spirit).flatMap(id -> MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, id)
                .map(definition -> new ActiveResource(id, definition))).ifPresent(active ->
                ResourceService.change(resources, active.id(), active.definition(), absorbed,
                        ResourceService.formulaContext(spirit, active.id(), active.definition(), context)));
    }

    private static void restoreAbsorption(LivingEntity entity, SpiritComponent spirit, ResourceHolderComponent resources,
                                          double absorbed, FormulaContext context) {
        activeResource(spirit).flatMap(id -> MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, id)
                .map(definition -> new ActiveResource(id, definition))).ifPresent(active ->
                ResourceService.change(resources, active.id(), active.definition(), absorbed,
                        ResourceService.formulaContext(entity, active.id(), active.definition(), context)));
    }

    private static Conversion conversion(ActiveResource active, FormulaContext context, SpiritComponent spirit,
                                         ResourceHolderComponent resources) {
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

    private static void convert(Optional<Identifier> resourceId, SpiritComponent spirit, ResourceHolderComponent resources,
                                FormulaContext context) {
        resourceId.flatMap(id -> MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, id)
                .map(definition -> new ActiveResource(id, definition))).ifPresent(active ->
                convert(active, spirit, resources, ResourceService.formulaContext(spirit, active.id(), active.definition(), context)));
    }

    private static void convert(Optional<Identifier> resourceId, SpiritComponent spirit, ResourceHolderComponent resources,
                                FormulaContext context, LivingEntity entity) {
        resourceId.flatMap(id -> MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, id)
                .map(definition -> new ActiveResource(id, definition))).ifPresent(active ->
                convert(active, spirit, resources, ResourceService.formulaContext(entity, active.id(), active.definition(), context)));
    }

    private static void convert(ActiveResource active, SpiritComponent spirit, ResourceHolderComponent resources,
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

    private static Optional<Identifier> activeResource(SpiritComponent spirit) {
        return spirit.realmStage().flatMap(id -> MxtDatapackRegistries.get(MxtResourceKeys.REALM_STAGE, id))
                .map(realm -> HolderHelper.id(realm.resource()));
    }

    public static boolean realmCultivateCondition(SpiritComponent spirit, LivingEntity entity, FormulaContext context) {
        return spirit.realmStage().map(stage -> stage.value().cultivateCondition().test(entity, context)).orElse(true);
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
