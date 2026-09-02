package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.cultivation.CultivateConditions;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.event.CultivationBreakEvent.Post;
import com.iafenvoy.mxt.event.CultivationBreakEvent.Pre;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtCriteriaTriggers;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.runtime.ability.AbilityEventBridge;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Evaluation;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Result;
import com.iafenvoy.mxt.runtime.tribulation.TribulationService;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Server-side breakthrough transaction. Content conditions are evaluated by callers before payment is committed.
 */
public final class CultivationService {
    private static final double PROGRESS_EPSILON = 1.0E-7D;

    private CultivationService() {
    }

    /**
     * Attempts the only legal next realm in the active resource's linear chain.
     */
    public static BreakthroughResult attempt(LivingEntity entity, CultivationAttachment spirit, ResourceHolderAttachment resources,
                                             Identifier resource, FormulaContext context, BooleanSupplier conditionsMet) {
        if (entity.level().isClientSide()) return BreakthroughResult.rejected(Failure.SERVER_ONLY, null);
        Transition transition = next(spirit, resource).orElse(null);
        if (transition == null) return BreakthroughResult.rejected(Failure.NO_NEXT_REALM, null);
        return attempt(entity, spirit, resources, transition, context, conditionsMet);
    }

    /**
     * Entity-aware breakthrough entry point. It preserves the same transaction semantics and
     * dispatches triggered abilities only after the realm state has committed.
     */
    private static BreakthroughResult attempt(LivingEntity entity, CultivationAttachment spirit, ResourceHolderAttachment resources, Transition transition,
                                              FormulaContext context, BooleanSupplier conditionsMet) {
        Holder<RealmStage> targetHolder = transition.target();
        Identifier targetId = HolderHelper.id(targetHolder);
        RealmStage target = targetHolder.value();
        if (!Objects.equals(target.resource(), activeResource(spirit, target)))
            return BreakthroughResult.rejected(Failure.WRONG_RESOURCE, null);
        FormulaContext resourceContext = ResourceService.formulaContext(entity, target.resource(), context);
        Threshold threshold = threshold(transition, resourceContext);
        if (threshold == null) return BreakthroughResult.rejected(Failure.INVALID_FORMULA, null);
        double minimum = threshold.breakthroughExp();
        double maximum = threshold.maxExperience();
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum < 0.0D || maximum < 0.0D || minimum > maximum)
            return BreakthroughResult.rejected(Failure.INVALID_FORMULA, null);
        double progress = spirit.cultivationProgress(target.resource());
        if (progress < minimum) return BreakthroughResult.rejected(Failure.INSUFFICIENT_PROGRESS, null);
        boolean configuredConditions = transition.conditions().test(entity, context);
        boolean requiredAbilities = RegistryCodecs.resolve(target.abilityRequirements(), MxtDatapackRegistries.registry(MxtResourceKeys.ABILITY))
                .allMatch(ability -> entity.getData(MxtAttachments.ABILITY_HOLDER).has(ability));
        BreakthroughResult result = commit(spirit, resources, transition, resourceContext,
                () -> configuredConditions && requiredAbilities && conditionsMet.getAsBoolean(), NeoForge.EVENT_BUS);
        if (result.advanced()) {
            if (entity instanceof ServerPlayer player) MxtCriteriaTriggers.BREAKTHROUGH.get().trigger(player, targetId);
            target.breakthroughParticle().ifPresent(effect -> {
                if (entity.level() instanceof ServerLevel level)
                    effect.send(level, entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D));
            });
            target.successAction().execute(entity, context);
            FormulaContext tribulationContext = context.with("aura_tribulation_modifier", AuraService.getPositionAura(entity.level(), entity.blockPosition()).rules().tribulationModify());
            target.tribulation().ifPresent(tribulation -> TribulationService.start(entity, entity.getData(MxtAttachments.TRIBULATION), tribulation, entity.level().getGameTime(), tribulationContext));
            AbilityEventBridge.onBreakthrough(entity, targetId, context);
        } else {
            target.failAction().execute(entity, context);
        }
        return result;
    }

    private static BreakthroughResult commit(CultivationAttachment spirit, ResourceHolderAttachment resources, @NotNull Transition transition,
                                             FormulaContext context, BooleanSupplier conditionsMet, @NotNull IEventBus eventBus) {
        Holder<RealmStage> targetHolder = transition.target();
        Identifier targetId = HolderHelper.id(targetHolder);
        RealmStage target = targetHolder.value();
        Threshold threshold = threshold(transition, context);
        if (threshold == null)
            return BreakthroughResult.rejected(Failure.INVALID_FORMULA, null);
        double minimum = threshold.breakthroughExp();
        Holder<Resource> targetResource = target.resource();
        double progress = spirit.cultivationProgress(targetResource);
        if (progress < minimum)
            return BreakthroughResult.rejected(Failure.INSUFFICIENT_PROGRESS, null);
        if (!conditionsMet.getAsBoolean()) return BreakthroughResult.rejected(Failure.CONDITIONS, null);
        Evaluation costs;
        try {
            costs = ResourceTransactions.evaluate(target.breakthroughCosts(), context);
        } catch (IllegalArgumentException exception) {
            return BreakthroughResult.rejected(Failure.INVALID_FORMULA, null);
        }
        Pre event = new Pre(spirit, resources, targetId, target, context, minimum, costs.amounts());
        if (eventBus.post(event).isCanceled()) return BreakthroughResult.rejected(Failure.CANCELLED, null);
        Result payment = ResourceTransactions.tryConsume(resources, new Evaluation(event.costs()));
        if (!payment.committed())
            return BreakthroughResult.rejected(Failure.INSUFFICIENT_RESOURCE, payment.failedResource());
        spirit.setRealmStage(targetHolder);
        spirit.setCultivationProgress(targetResource, 0.0D);
        eventBus.post(new Post(spirit, resources, targetId, target, context, minimum, payment.amounts()));
        return BreakthroughResult.committed(payment.amounts());
    }

    private static Optional<Transition> next(CultivationAttachment spirit, Identifier resource) {
        Holder<RealmStage> current = spirit.realmStages().values().stream()
                .filter(value -> HolderHelper.id(value.value().resource()).equals(resource)).findFirst().orElse(null);
        if (current != null) {
            return current.value().nextRealm().filter(value -> HolderHelper.id(value.value().resource()).equals(resource))
                    .map(value -> Transition.realm(current, value));
        }
        Resource definition = MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, resource).orElse(null);
        return definition == null ? Optional.empty() : definition.firstRealm()
                .filter(value -> HolderHelper.id(value.value().resource()).equals(resource))
                .map(value -> Transition.mortal(definition, value));
    }

    /**
     * Adds cultivation progress while respecting the active transition's upper bound.
     */
    public static double addProgress(LivingEntity entity, Holder<Resource> resource, double amount, FormulaContext context) {
        if (!Double.isFinite(amount) || amount <= 0.0D) return 0.0D;
        CultivationAttachment spirit = entity.getData(MxtAttachments.CULTIVATION);
        Transition transition = next(spirit, HolderHelper.id(resource)).orElse(null);
        if (transition == null) return 0.0D;
        FormulaContext resourceContext = ResourceService.formulaContext(entity, resource, context);
        Threshold threshold = threshold(transition, resourceContext);
        if (threshold == null) return 0.0D;
        double maximum = threshold.maxExperience();
        double before = spirit.cultivationProgress(resource);
        if (before >= maximum - PROGRESS_EPSILON) {
            if (Double.compare(before, maximum) != 0) spirit.setCultivationProgress(resource, maximum);
            return 0.0D;
        }
        double accepted = Math.max(0.0D, Math.min(amount, maximum - before));
        if (maximum - (before + accepted) <= PROGRESS_EPSILON) accepted = maximum - before;
        if (accepted > 0.0D) spirit.setCultivationProgress(resource, before + accepted);
        return accepted;
    }

    /**
     * Context-only variant used by server-side service paths without an entity reference.
     */
    public static double addProgress(CultivationAttachment spirit, Holder<Resource> resource, double amount, FormulaContext context) {
        if (!Double.isFinite(amount) || amount <= 0.0D) return 0.0D;
        Transition transition = next(spirit, HolderHelper.id(resource)).orElse(null);
        if (transition == null) return 0.0D;
        Threshold threshold = threshold(transition, context);
        if (threshold == null) return 0.0D;
        double maximum = threshold.maxExperience();
        double before = spirit.cultivationProgress(resource);
        if (before >= maximum - PROGRESS_EPSILON) {
            if (Double.compare(before, maximum) != 0) spirit.setCultivationProgress(resource, maximum);
            return 0.0D;
        }
        double accepted = Math.max(0.0D, Math.min(amount, maximum - before));
        if (maximum - (before + accepted) <= PROGRESS_EPSILON) accepted = maximum - before;
        if (accepted > 0.0D) spirit.setCultivationProgress(resource, before + accepted);
        return accepted;
    }

    /**
     * Returns remaining legal progress for this resource's current transition.
     */
    public static double remainingProgressCapacity(CultivationAttachment spirit, Holder<Resource> resource, FormulaContext context) {
        Transition transition = next(spirit, HolderHelper.id(resource)).orElse(null);
        if (transition == null) return 0.0D;
        Threshold threshold = threshold(transition, context);
        if (threshold == null) return 0.0D;
        double maximum = threshold.maxExperience();
        double current = spirit.cultivationProgress(resource);
        double remaining = maximum - current;
        if (remaining >= 0.0D && remaining <= PROGRESS_EPSILON) {
            if (Double.compare(current, maximum) != 0) spirit.setCultivationProgress(resource, maximum);
            return 0.0D;
        }
        return remaining <= PROGRESS_EPSILON ? 0.0D : remaining;
    }

    /**
     * Resolves the current resource's breakthrough state without mutating the player.
     * This is shared by the automatic-breakthrough tick and the information screen.
     */
    public static BreakthroughStatus breakthroughStatus(LivingEntity entity, Holder<Resource> resource, FormulaContext context) {
        Transition transition = next(entity.getData(MxtAttachments.CULTIVATION), HolderHelper.id(resource)).orElse(null);
        if (transition == null) return BreakthroughStatus.UNAVAILABLE;
        Threshold threshold = threshold(transition, ResourceService.formulaContext(entity, resource, context));
        if (threshold == null) return BreakthroughStatus.UNAVAILABLE;
        double progress = entity.getData(MxtAttachments.CULTIVATION).cultivationProgress(resource);
        boolean reached = progress + PROGRESS_EPSILON >= threshold.breakthroughExp();
        boolean conditions = reached && transition.conditions().test(entity, context);
        return new BreakthroughStatus(reached, conditions, transition.autoBreakthrough(),
                threshold.breakthroughExp(), threshold.maxExperience());
    }

    /**
     * Returns the conditions belonging to the currently pending transition.
     * The returned value is datapack state; runtime trigger subscriptions are
     * rebuilt separately and are never stored in the attachment.
     */
    public static Optional<CultivateConditions> pendingConditions(LivingEntity entity, Holder<Resource> resource) {
        Transition transition = next(entity.getData(MxtAttachments.CULTIVATION), HolderHelper.id(resource)).orElse(null);
        return transition == null ? Optional.empty() : Optional.of(transition.conditions());
    }

    /**
     * Sets a validated realm administratively without traversing the registry at call time.
     */
    public static boolean setRealm(CultivationAttachment spirit, Identifier target) {
        ServerCache cache = ServerCache.get().orElse(null);
        Identifier resource = cache == null ? null : cache.resourceForRealm(target).orElse(null);
        if (resource == null) return false;
        Holder<RealmStage> current = spirit.realmStages().values().stream()
                .filter(stage -> cache.resourceForRealm(HolderHelper.id(stage)).filter(resource::equals).isPresent())
                .findFirst().orElse(null);
        Holder<RealmStage> targetHolder = MxtDatapackRegistries.holder(MxtResourceKeys.REALM_STAGE, target).orElse(null);
        if (targetHolder == null) return false;
        spirit.setRealmStage(targetHolder);
        spirit.setCultivationProgress(targetHolder.value().resource(), 0.0D);
        return true;
    }

    private static Holder<Resource> activeResource(CultivationAttachment spirit, RealmStage target) {
        return spirit.realmStages().values().stream().filter(stage -> stage.value().resource().equals(target.resource()))
                .findFirst().map(Holder::value).map(RealmStage::resource).orElseGet(target::resource);
    }

    private static Threshold threshold(Transition transition, FormulaContext context) {
        double breakthroughExp;
        double maxExperience;
        if (transition.mortal()) {
            breakthroughExp = transition.resource().startExp().evaluate(context);
            maxExperience = breakthroughExp;
        } else {
            RealmStage stage = transition.current().value();
            breakthroughExp = stage.breakthroughExp().evaluate(context);
            maxExperience = stage.maxExperience().evaluate(context);
        }
        return Double.isFinite(breakthroughExp) && Double.isFinite(maxExperience)
                && breakthroughExp >= 0.0D && maxExperience >= breakthroughExp
                ? new Threshold(breakthroughExp, maxExperience) : null;
    }

    private record Threshold(double breakthroughExp, double maxExperience) {
    }

    private record Transition(@NotNull Holder<RealmStage> current, @NotNull Holder<RealmStage> target,
                              @NotNull Resource resource, boolean mortal) {
        private static Transition realm(Holder<RealmStage> current, Holder<RealmStage> target) {
            return new Transition(current, target, current.value().resource().value(), false);
        }

        private static Transition mortal(Resource resource, Holder<RealmStage> target) {
            return new Transition(target, target, resource, true);
        }

        private CultivateConditions conditions() {
            return this.mortal ? this.target.value().breakthrough() : this.current.value().breakthrough();
        }

        private boolean autoBreakthrough() {
            return this.mortal ? this.target.value().autoBreakthrough() : this.current.value().autoBreakthrough();
        }

    }

    public record BreakthroughStatus(boolean reached, boolean conditionsMet, boolean automatic,
                                     double minimumExperience, double maximumExperience) {
        private static final BreakthroughStatus UNAVAILABLE = new BreakthroughStatus(false, false, false, 0.0D, 0.0D);
    }

    public enum Failure {
        DISABLED, WRONG_RESOURCE, NO_NEXT_REALM, INSUFFICIENT_PROGRESS, MAX_PROGRESS, CONDITIONS, INSUFFICIENT_RESOURCE, INVALID_FORMULA, CANCELLED, SERVER_ONLY
    }

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
