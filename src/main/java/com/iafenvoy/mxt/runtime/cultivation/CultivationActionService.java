package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.AuraChunkAttachment;
import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraGain;
import com.iafenvoy.mxt.data.aura.AuraRequirement;
import com.iafenvoy.mxt.data.condition.builtin.entity.AuraRangeEntityCondition;
import com.iafenvoy.mxt.data.cost.CostTransaction;
import com.iafenvoy.mxt.data.cost.Costs;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostOrigin;
import com.iafenvoy.mxt.data.cultivation.CultivateAction;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.aura.AuraLookup;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.resource.ResourceService.Bounds;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Evaluation;
import com.iafenvoy.mxt.runtime.trigger.CultivationTriggerService;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BooleanSupplier;

/**
 * Authoritative lifecycle for the selected cultivation action; each realm resource chain is processed
 * independently while the action runs.
 */
//TODO::May be removed together with CultivateAction - see that record for the cluster it lives in.
public final class CultivationActionService {
    private CultivationActionService() {
    }

    public static Result start(CultivationAttachment spirit, @NotNull Identifier actionId, CultivateAction definition,
                               long gameTime, BooleanSupplier conditionsMet) {
        Holder<CultivateAction> action = MxtDatapackRegistries.holder(MxtResourceKeys.CULTIVATE_ACTION, actionId).orElse(null);
        if (action == null) return Result.rejected(Failure.DISABLED, null);
        return start(spirit, action, definition, gameTime, conditionsMet);
    }

    public static Result start(CultivationAttachment spirit, @NotNull Holder<CultivateAction> action, CultivateAction definition,
                               long gameTime, BooleanSupplier conditionsMet) {
        if (spirit.cultivating()) return Result.rejected(Failure.ALREADY_ACTIVE, null);
        if (spirit.isCultivateActionOnCooldown(action, gameTime)) return Result.rejected(Failure.COOLDOWN, null);
        if (!conditionsMet.getAsBoolean()) return Result.rejected(Failure.CONDITIONS, null);
        spirit.startCultivateAction(action, gameTime, gameTime);
        return Result.startedResult();
    }

    public static Result start(LivingEntity entity, CultivationAttachment spirit, Identifier actionId, CultivateAction definition,
                               long gameTime, FormulaContext context) {
        boolean conditions = definition.startCondition().test(entity, context) && canStartCultivation(entity, context);
        return start(spirit, actionId, definition, gameTime, () -> conditions);
    }

    public static boolean canStartCultivation(LivingEntity entity, FormulaContext context) {
        return AuraLookup.all(entity).map(Reference::value)
                .filter(aura -> aura.firstRealm().isPresent())
                .allMatch(aura -> aura.startCultivateConditions().test(entity, context));
    }

    // Every resource and aura requirement is checked before anything mutates.
    public static Result tick(CultivationAttachment spirit, ResourceHolderAttachment resources, AuraChunkAttachment aura, Identifier actionId,
                              CultivateAction definition, long gameTime, FormulaContext context,
                              BooleanSupplier conditionsMet) {
        return tick(spirit, resources, aura, actionId, definition, gameTime, context, conditionsMet, 1.0D);
    }

    // Only the spirit-root and technique cultivation modifiers apply on this path.
    public static Result tick(LivingEntity entity, CultivationAttachment spirit, ResourceHolderAttachment resources, AuraChunkAttachment aura, Identifier actionId,
                              CultivateAction definition, long gameTime, FormulaContext context,
                              BooleanSupplier conditionsMet) {
        double affinity = CultivationAffinity.multiplier(entity.getData(MxtAttachments.SPIRIT_IDENTITY), aura, context);
        return tick(spirit, resources, aura, actionId, definition, gameTime, context, conditionsMet, affinity);
    }

    public static Result tick(LivingEntity entity, CultivationAttachment spirit, ResourceHolderAttachment resources, AuraResult aura, Identifier actionId,
                              CultivateAction definition, long gameTime, FormulaContext context,
                              BooleanSupplier conditionsMet) {
        Holder<CultivateAction> action = MxtDatapackRegistries.holder(MxtResourceKeys.CULTIVATE_ACTION, actionId).orElse(null);
        if (action == null) return Result.rejected(Failure.DISABLED, null);
        return tick(entity, spirit, resources, aura, action, definition, gameTime, context, conditionsMet);
    }

    public static Result tick(LivingEntity entity, CultivationAttachment spirit, ResourceHolderAttachment resources, AuraResult aura, Holder<CultivateAction> action,
                              CultivateAction definition, long gameTime, FormulaContext context, BooleanSupplier conditionsMet) {
        Identifier actionId = HolderHelper.id(action);
        if (!canCultivateInEnvironment(spirit, entity, aura, context))
            return stop(entity, spirit, action, definition, gameTime, Failure.ENVIRONMENT);
        double affinity = CultivationAffinity.multiplier(entity.getData(MxtAttachments.SPIRIT_IDENTITY), aura, context);
        return tick(entity, spirit, resources, aura, action, definition, gameTime, context, conditionsMet, affinity);
    }

    private static Result tick(LivingEntity entity, CultivationAttachment spirit, ResourceHolderAttachment resources, AuraResult aura, Holder<CultivateAction> action,
                               CultivateAction definition, long gameTime, FormulaContext context,
                               BooleanSupplier conditionsMet, double affinity) {
        if (!spirit.cultivating() || spirit.cultivateAction().filter(action::equals).isEmpty())
            return Result.rejected(Failure.NOT_ACTIVE, null);
        if (!conditionsMet.getAsBoolean())
            return stop(entity, spirit, action, definition, gameTime, Failure.CONDITIONS);
        ItemAuraService.tick(entity, resources, context);
        Recovery recovery = recover(entity, spirit, resources, aura, definition, affinity, context);
        if (!recovery.valid()) return stop(entity, spirit, action, definition, gameTime, Failure.INVALID_FORMULA);
        convertAll(entity, spirit, resources, context);
        if (gameTime < spirit.nextCultivateTick()) {
            return Result.waitingResult();
        }
        // The aura a cycle burns comes out of the ground at the cultivator, so the pool is the one channel this
        // plan uses; the shared-pool allocation below scales the amounts before they are committed.
        CostContext auraContext = CostContext.pool(entity, entity.level(), entity.blockPosition(), context, CostOrigin.CULTIVATION);
        // Whatever size is available is not asked here: this tick's share is decided below, and the commit is
        // what checks the pool against the amount actually spent.
        CostTransaction.Planning auraPlan = CostTransaction.planDeferred(definition.auraCosts(), auraContext);
        if (!auraPlan.ok()) return stop(entity, spirit, action, definition, gameTime, Failure.INVALID_FORMULA);
        Map<Holder<Aura>, Double> auraCosts = auraPlan.auras();
        double auraCost = auraCosts.values().stream().mapToDouble(Double::doubleValue).sum();
        if (!Double.isFinite(affinity) || affinity < 0.0D)
            return stop(entity, spirit, action, definition, gameTime, Failure.INVALID_FORMULA);
        CostContext costContext = CostContext.of(entity, context, CostOrigin.CULTIVATION);
        CostTransaction.Planning costPlan = CostTransaction.plan(definition.costs(), costContext);
        if (!costPlan.ok()) return stop(entity, spirit, action, definition, gameTime, Failure.INVALID_FORMULA);
        Evaluation costs = Evaluation.of(costPlan.resources());
        Map<Holder<Aura>, Double> gains;
        try {
            gains = evaluateGains(definition.auraGains(), context);
        } catch (IllegalArgumentException | IllegalStateException error) {
            return stop(entity, spirit, action, definition, gameTime, Failure.INVALID_FORMULA);
        }
        double allocationFactor = 1.0D;
        if (auraCost > 0.0D && entity instanceof ServerPlayer player) {
            // The level prepass bounds this from the shared chunk pool; direct callers keep full cost.
            allocationFactor = AuraDistributionService.take(player).map(values -> allocationFactor(auraCosts, values)).orElse(1.0D);
            if (allocationFactor <= 0.0D && MxtServerConfig.INSTANCE.cultivation.forbidWithoutEligibleAura.getValue())
                return stop(entity, spirit, action, definition, gameTime, Failure.INSUFFICIENT_AURA);
        }
        // The prepass bounds what this tick may take from the shared pool; the plan is what actually pays it.
        double share = allocationFactor;
        auraPlan.auras().replaceAll((element, amount) -> amount * share);
        double speed = aura.cultivationSpeed() * allocationFactor;
        gains.replaceAll((id, amount) -> amount * speed);
        if (!Double.isFinite(speed) || speed < 0.0D)
            return stop(entity, spirit, action, definition, gameTime, Failure.INVALID_FORMULA);
        ResourceTransactions.Result preview = ResourceTransactions.tryConsume(copyOf(resources), costs);
        if (!preview.committed()) return Result.rejected(Failure.INSUFFICIENT_RESOURCE, preview.failedResource());
        if (!canApplyGains(entity, copyOf(resources), gains, context))
            return stop(entity, spirit, action, definition, gameTime, Failure.INVALID_FORMULA);
        if (!auraPlan.auras().isEmpty() && !CostTransaction.commit(auraPlan, auraContext).paid())
            return Result.rejected(Failure.INSUFFICIENT_AURA, null);
        CostTransaction.PayResult payment = CostTransaction.commit(costPlan, costContext, resources);
        if (!payment.paid()) return Result.rejected(Failure.INSUFFICIENT_RESOURCE, payment.failedResource());
        applyGains(entity, resources, gains, context);
        spirit.scheduleCultivateTick(Math.addExact(gameTime, definition.tickInterval()));
        definition.tickAction().execute(entity, context);
        return Result.progressed(recovery.cultivation(), payment.resources());
    }

    private static Result tick(CultivationAttachment spirit, ResourceHolderAttachment resources, AuraChunkAttachment aura, Identifier actionId,
                               CultivateAction definition, long gameTime, FormulaContext context,
                               BooleanSupplier conditionsMet, double affinity) {
        Holder<CultivateAction> action = MxtDatapackRegistries.holder(MxtResourceKeys.CULTIVATE_ACTION, actionId).orElse(null);
        if (action == null) return Result.rejected(Failure.DISABLED, null);
        if (!spirit.cultivating() || spirit.cultivateAction().filter(action::equals).isEmpty())
            return Result.rejected(Failure.NOT_ACTIVE, null);
        if (!conditionsMet.getAsBoolean()) return stop(spirit, actionId, definition, gameTime, Failure.CONDITIONS);
        if (gameTime < spirit.nextCultivateTick()) {
            convertAll(spirit, resources, context);
            return Result.waitingResult();
        }
        double gain = definition.absorbAmount().evaluate(context) * affinity;
        Map<Holder<Aura>, Double> auraCosts = evaluateAuraCosts(definition, context);
        if (auraCosts == null) return stop(spirit, actionId, definition, gameTime, Failure.INVALID_FORMULA);
        double auraCost = auraCosts.values().stream().mapToDouble(Double::doubleValue).sum();
        if (!Double.isFinite(affinity) || affinity < 0.0D || !Double.isFinite(gain) || gain < 0.0D)
            return stop(spirit, actionId, definition, gameTime, Failure.INVALID_FORMULA);
        CostContext costContext = CostContext.account(resources, null, context, CostOrigin.CULTIVATION);
        CostTransaction.Planning costPlan = CostTransaction.plan(definition.costs(), costContext, resources, null);
        if (!costPlan.ok()) return stop(spirit, actionId, definition, gameTime, Failure.INVALID_FORMULA);
        Evaluation costs = Evaluation.of(costPlan.resources());
        Map<Holder<Aura>, Double> gains;
        try {
            gains = evaluateGains(definition.auraGains(), context);
        } catch (IllegalArgumentException | IllegalStateException error) {
            return stop(spirit, actionId, definition, gameTime, Failure.INVALID_FORMULA);
        }
        ResourceTransactions.Result preview = ResourceTransactions.tryConsume(copyOf(resources), costs);
        if (!preview.committed()) return Result.rejected(Failure.INSUFFICIENT_RESOURCE, preview.failedResource());
        if (!canApplyGains(copyOf(resources), gains, context) || !canConvertAbsorption(spirit, resources, gain, context))
            return stop(spirit, actionId, definition, gameTime, Failure.INVALID_FORMULA);
        if (!hasAura(aura, auraCosts)) return Result.rejected(Failure.INSUFFICIENT_AURA, null);
        CostTransaction.PayResult payment = CostTransaction.commit(costPlan, costContext, resources);
        if (!payment.paid()) return Result.rejected(Failure.INSUFFICIENT_RESOURCE, payment.failedResource());
        aura.consume(auraCosts);
        restoreAbsorption(spirit, resources, gain, context);
        convertAll(spirit, resources, context);
        applyGains(resources, gains, context);
        spirit.scheduleCultivateTick(Math.addExact(gameTime, definition.tickInterval()));
        return Result.progressed(gain, payment.resources());
    }

    public static Result stop(CultivationAttachment spirit, Identifier actionId, CultivateAction definition, long gameTime) {
        return stop(spirit, actionId, definition, gameTime, null);
    }

    public static Result stop(LivingEntity entity, CultivationAttachment spirit, Identifier actionId, CultivateAction definition, long gameTime) {
        Holder<CultivateAction> action = MxtDatapackRegistries.holder(MxtResourceKeys.CULTIVATE_ACTION, actionId).orElse(null);
        return action == null ? Result.rejected(Failure.DISABLED, null) : stop(entity, spirit, action, definition, gameTime, null);
    }

    private static Result stop(CultivationAttachment spirit, Identifier actionId, CultivateAction definition, long gameTime, Failure reason) {
        Holder<CultivateAction> action = MxtDatapackRegistries.holder(MxtResourceKeys.CULTIVATE_ACTION, actionId).orElse(null);
        return action == null ? Result.rejected(Failure.DISABLED, null) : stop(spirit, action, definition, gameTime, reason);
    }

    private static Result stop(CultivationAttachment spirit, Holder<CultivateAction> action, CultivateAction definition, long gameTime, Failure reason) {
        spirit.stopCultivateAction(action, Math.addExact(gameTime, definition.cooldownTicks()));
        return reason == null ? Result.stoppedResult() : Result.rejected(reason, null);
    }

    private static Result stop(LivingEntity entity, CultivationAttachment spirit, Holder<CultivateAction> action, CultivateAction definition,
                               long gameTime, Failure reason) {
        ItemAuraService.returnFloatingItem(entity);
        // Breakthrough listeners are derived runtime state and must disappear as soon as cultivation
        // stops, including on failure paths inside the tick.
        CultivationTriggerService.clear(entity);
        return stop(spirit, action, definition, gameTime, reason);
    }

    private static ResourceHolderAttachment copyOf(ResourceHolderAttachment source) {
        ResourceHolderAttachment copy = new ResourceHolderAttachment();
        source.values().forEach(copy::set);
        return copy;
    }

    private static Map<Holder<Aura>, Double> evaluateGains(List<AuraGain> entries, FormulaContext context) {
        Map<Holder<Aura>, Double> amounts = new LinkedHashMap<>();
        for (AuraGain entry : entries) amounts.merge(entry.aura(), entry.evaluate(context), Double::sum);
        return amounts;
    }

    private static Map<Holder<Aura>, Double> evaluateAuraCosts(CultivateAction definition,
                                                               FormulaContext context) {
        // Paid by hand here: this path holds the chunk store instead of a level, so it only needs the amounts.
        return Costs.auras(definition.auraCosts(), CostContext.of(null, context, CostOrigin.CULTIVATION));
    }

    private static boolean hasAura(AuraChunkAttachment aura, Map<Holder<Aura>, Double> costs) {
        return costs.entrySet().stream().allMatch(entry -> aura.auras().getOrDefault(entry.getKey(), AuraPool.empty()).amount() >= entry.getValue());
    }

    // Realm chains are regenerated by cultivation itself, so each value's overflow can be committed to that
    // chain's cultivation progress.
    public static boolean handlesNaturalRegeneration(LivingEntity entity, Holder<Aura> aura) {
        CultivationAttachment spirit = entity.getData(MxtAttachments.CULTIVATION);
        Holder<RealmStage> stage = stageFor(spirit, aura);
        return spirit.cultivating() && stage != null
                && stage.value().cultivateCondition().test(entity, FormulaContexts.forEntity(entity));
    }

    // Non-aura conditions stay strict, while each aura-range entry counts as an independent eligible source.
    // Servers may require at least one eligible source through the cultivation configuration.
    public static boolean canCultivateInEnvironment(CultivationAttachment spirit, LivingEntity entity,
                                                    AuraResult aura, FormulaContext context) {
        if (aura.suppressCultivate()) return false;
        boolean realmEligible = cultivationStages(entity, spirit).stream()
                .anyMatch(stage -> stage.value().cultivateCondition().test(entity, context));
        if (!realmEligible && MxtServerConfig.INSTANCE.cultivation.forbidWithoutEligibleAura.getValue()) return false;
        if (!(aura.cultivateCondition() instanceof AuraRangeEntityCondition(
                Map<Holder<Aura>, AuraRequirement> aura1
        )))
            return aura.cultivateCondition().test(entity, context);
        boolean anyEligible = aura1.isEmpty() || aura1.entrySet().stream()
                .anyMatch(entry -> entry.getValue().test(aura.pool(entry.getKey()).amount(), context));
        return anyEligible || !MxtServerConfig.INSTANCE.cultivation.forbidWithoutEligibleAura.getValue();
    }

    // Every capacity is filled first, and only that value's overflow becomes its cultivation progress.
    private static Recovery recover(LivingEntity entity, CultivationAttachment spirit, ResourceHolderAttachment resources,
                                    AuraResult aura, CultivateAction action, double affinity, FormulaContext context) {
        double restored = 0.0D;
        double cultivation = 0.0D;
        double multiplier = action.absorbAmount().evaluate(context);
        if (!Double.isFinite(multiplier) || multiplier < 0.0D || !Double.isFinite(affinity) || affinity < 0.0D)
            return Recovery.INVALID;
        for (Holder<Aura> chain : eligibleRealmCultivations(spirit, entity, context)) {
            // The parameter is the ambient aura read for this position, so the chain's own definition needs a
            // name of its own here.
            Aura definition = chain.value();
            Holder<Resource> resource = definition.resource();
            FormulaContext resourceContext = ResourceService.formulaContext(entity, resource, context);
            double regen = definition.regen().evaluate(resourceContext);
            double recovered = regen * multiplier * affinity * aura.cultivationSpeed();
            if (!Double.isFinite(regen) || !Double.isFinite(recovered)) return Recovery.INVALID;
            if (recovered <= 0.0D) continue;
            if (!ResourceService.initialize(resources, resource, resourceContext).valid()) return Recovery.INVALID;
            double before = resources.get(resource);
            ResourceService.Result result = ResourceService.change(resources, resource, recovered, resourceContext);
            if (!result.valid()) return Recovery.INVALID;
            double stored = Math.max(0.0D, result.value() - before);
            double overflow = Math.max(0.0D, recovered - stored);
            if (overflow > 0.0D) CultivationService.addProgressForChain(entity, chain, overflow, resourceContext);
            restored += stored;
            cultivation += overflow;
        }
        return new Recovery(restored, cultivation, true);
    }

    // Each requested aura source contributes independently; a missing source reduces only its own share
    // instead of rejecting the whole tick.
    private static double allocationFactor(Map<Holder<Aura>, Double> requested,
                                           Map<Holder<Aura>, Double> allocated) {
        return requested.entrySet().stream()
                .filter(entry -> entry.getValue() > 0.0D)
                .mapToDouble(entry -> Math.clamp(allocated.getOrDefault(entry.getKey(), 0.0D) / entry.getValue(), 0.0D, 1.0D))
                .average().orElse(1.0D);
    }

    private static boolean canApplyGains(ResourceHolderAttachment resources, Map<Holder<Aura>, Double> gains, FormulaContext context) {
        for (Entry<Holder<Aura>, Double> gain : gains.entrySet()) {
            Holder<Resource> resource = gain.getKey().value().resource();
            if (!ResourceService.change(resources, resource, gain.getValue(), context).valid()) return false;
        }
        return true;
    }

    private static boolean canApplyGains(LivingEntity entity, ResourceHolderAttachment resources, Map<Holder<Aura>, Double> gains,
                                         FormulaContext context) {
        for (Entry<Holder<Aura>, Double> gain : gains.entrySet()) {
            Holder<Resource> resource = gain.getKey().value().resource();
            if (!ResourceService.change(resources, resource, gain.getValue(),
                    ResourceService.formulaContext(entity, resource, context)).valid())
                return false;
        }
        return true;
    }

    private static void applyGains(ResourceHolderAttachment resources, Map<Holder<Aura>, Double> gains, FormulaContext context) {
        for (Entry<Holder<Aura>, Double> gain : gains.entrySet())
            ResourceService.change(resources, gain.getKey().value().resource(), gain.getValue(), context);
    }

    private static void applyGains(LivingEntity entity, ResourceHolderAttachment resources, Map<Holder<Aura>, Double> gains,
                                   FormulaContext context) {
        for (Entry<Holder<Aura>, Double> gain : gains.entrySet()) {
            Holder<Resource> resource = gain.getKey().value().resource();
            ResourceService.change(resources, resource, gain.getValue(),
                    ResourceService.formulaContext(entity, resource, context));
        }
    }

    private static boolean canConvertAbsorption(CultivationAttachment spirit, ResourceHolderAttachment resources, double absorbed,
                                                FormulaContext context) {
        return realmCultivations(spirit).stream().allMatch(aura -> conversion(
                active(aura), context, spirit, resources).valid());
    }

    private static boolean canConvertAbsorption(LivingEntity entity, CultivationAttachment spirit, ResourceHolderAttachment resources,
                                                double absorbed, FormulaContext context) {
        return realmCultivations(spirit).stream().allMatch(aura -> {
            ActiveCultivation active = active(aura);
            return conversion(active, ResourceService.formulaContext(entity, active.resource(), context), spirit, resources).valid();
        });
    }

    // Absorption restores every chain's value independently; each conversion direction has its own source-side
    // per-tick limit.
    private static void restoreAbsorption(CultivationAttachment spirit, ResourceHolderAttachment resources, double absorbed,
                                          FormulaContext context) {
        for (Holder<Aura> aura : realmCultivations(spirit)) {
            ActiveCultivation active = active(aura);
            ResourceService.change(resources, active.id(), absorbed,
                    ResourceService.formulaContext(spirit, active.resource(), context));
        }
    }

    private static void restoreAbsorption(LivingEntity entity, CultivationAttachment spirit, ResourceHolderAttachment resources,
                                          double absorbed, FormulaContext context) {
        for (Holder<Aura> aura : realmCultivations(spirit)) {
            ActiveCultivation active = active(aura);
            ResourceService.change(resources, active.id(), absorbed,
                    ResourceService.formulaContext(entity, active.resource(), context));
        }
    }

    private static Conversion conversion(ActiveCultivation active, FormulaContext context, CultivationAttachment spirit,
                                         ResourceHolderAttachment resources) {
        Aura aura = active.auraValue();
        double cultivationToResource = aura.cultivationToResource().multiplier().evaluate(context);
        double cultivationToResourceMaxPerTick = aura.cultivationToResource().maxPerTick().evaluate(context);
        double resourceToCultivation = aura.resourceToCultivation().multiplier().evaluate(context);
        double resourceToCultivationMaxPerTick = aura.resourceToCultivation().maxPerTick().evaluate(context);
        return Double.isFinite(cultivationToResource) && cultivationToResource >= 0.0D
                && Double.isFinite(cultivationToResourceMaxPerTick) && cultivationToResourceMaxPerTick >= 0.0D
                && Double.isFinite(resourceToCultivation) && resourceToCultivation >= 0.0D
                && Double.isFinite(resourceToCultivationMaxPerTick) && resourceToCultivationMaxPerTick >= 0.0D
                && ResourceService.resolveBounds(active.definition(), context).isPresent()
                ? new Conversion(cultivationToResource, cultivationToResourceMaxPerTick,
                resourceToCultivation, resourceToCultivationMaxPerTick) : Conversion.INVALID;
    }

    private static void convertAll(CultivationAttachment spirit, ResourceHolderAttachment resources, FormulaContext context) {
        for (Holder<Aura> aura : realmCultivations(spirit)) {
            ActiveCultivation active = active(aura);
            convert(active, spirit, resources, ResourceService.formulaContext(spirit, active.resource(), context));
        }
    }

    private static void convertAll(LivingEntity entity, CultivationAttachment spirit, ResourceHolderAttachment resources,
                                   FormulaContext context) {
        for (Holder<Aura> chain : eligibleRealmCultivations(spirit, entity, context)) {
            ActiveCultivation active = active(chain);
            convert(active, spirit, resources, ResourceService.formulaContext(entity, active.resource(), context));
        }
    }

    private static void convert(ActiveCultivation active, CultivationAttachment spirit, ResourceHolderAttachment resources,
                                FormulaContext context) {
        Conversion conversion = conversion(active, context, spirit, resources);
        if (!conversion.valid()) return;
        Holder<Aura> aura = active.aura();
        Resource definition = active.definition();
        Holder<Resource> resource = active.resource();
        Bounds bounds = ResourceService.resolveBounds(definition, context).orElseThrow();

        // While cultivating, stored resource is the preferred source for progress; when it is depleted
        // the reverse conversion can restore a usable amount.
        if (conversion.resourceToCultivation() > 0.0D) {
            double available = Math.max(0.0D, resources.get(resource) - bounds.min());
            double remainingProgress = CultivationService.remainingProgressForChain(spirit, aura, context);
            if (remainingProgress <= 0.0D) return;
            double consumed = Math.min(conversion.resourceToCultivationMaxPerTick(),
                    Math.min(available, remainingProgress / conversion.resourceToCultivation()));
            if (consumed > 0.0D) {
                ResourceService.change(resources, active.id(), -consumed, context);
                CultivationService.addProgressForChain(spirit, aura, consumed * conversion.resourceToCultivation(), context);
                return;
            }
        }

        if (conversion.cultivationToResource() > 0.0D) {
            double capacity = Math.max(0.0D, bounds.max() - resources.get(resource));
            double extracted = Math.min(conversion.cultivationToResourceMaxPerTick(),
                    Math.min(spirit.cultivationProgress(aura), capacity / conversion.cultivationToResource()));
            if (extracted > 0.0D) {
                spirit.setCultivationProgress(aura, Math.max(0.0D, spirit.cultivationProgress(aura) - extracted));
                ResourceService.change(resources, active.id(), extracted * conversion.cultivationToResource(), context);
            }
        }
    }

    private static List<Holder<Aura>> realmCultivations(CultivationAttachment spirit) {
        return spirit.realmStages().values().stream()
                .map(stage -> stage.value().aura()).distinct().toList();
    }

    private static List<Holder<Aura>> eligibleRealmCultivations(CultivationAttachment spirit, LivingEntity entity,
                                                                FormulaContext context) {
        return cultivationStages(entity, spirit).stream()
                .filter(stage -> stage.value().cultivateCondition().test(entity, context))
                .map(stage -> stage.value().aura()).distinct().toList();
    }

    public static boolean realmCultivateCondition(CultivationAttachment spirit, LivingEntity entity, FormulaContext context) {
        return cultivationStages(entity, spirit).stream().anyMatch(stage -> stage.value().cultivateCondition().test(entity, context));
    }

    /**
     * The active stage, falling back to the aura's first realm for mortals.
     */
    private static Holder<RealmStage> stageFor(CultivationAttachment spirit, Holder<Aura> aura) {
        Holder<RealmStage> current = spirit.realmStage(aura);
        return current != null ? current : aura.value().firstRealm().orElse(null);
    }

    private static List<Holder<RealmStage>> cultivationStages(LivingEntity entity, CultivationAttachment spirit) {
        Map<Holder<Aura>, Holder<RealmStage>> stages = new LinkedHashMap<>(spirit.realmStages());
        AuraLookup.all(entity).forEach(aura ->
                aura.value().firstRealm().ifPresent(first -> stages.putIfAbsent(aura, first)));
        return List.copyOf(stages.values());
    }

    private static ActiveCultivation active(Holder<Aura> aura) {
        return new ActiveCultivation(aura);
    }

    private record ActiveCultivation(Holder<Aura> aura) {
        private Identifier id() {
            return HolderHelper.id(this.aura.value().resource());
        }

        private Resource definition() {
            return this.aura.value().resource().value();
        }

        private Holder<Resource> resource() {
            return this.aura.value().resource();
        }

        private Aura auraValue() {
            return this.aura.value();
        }
    }

    private record Recovery(double restored, double cultivation, boolean valid) {
        private static final Recovery NONE = new Recovery(0.0D, 0.0D, true);
        private static final Recovery INVALID = new Recovery(0.0D, 0.0D, false);
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

        static Result rejected(Failure failure, Identifier resource) {
            return new Result(false, false, false, false, failure, resource, 0.0D, Map.of());
        }
    }
}
