package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.cultivation.CultivateConditions;
import com.iafenvoy.mxt.data.cultivation.CultivationProfile;
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
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Server-side breakthrough transaction. Content conditions are evaluated by callers before payment is committed.
 *
 * <p>A chain is identified by its cultivation profile, so the profile-keyed methods are the core and
 * the value-keyed overloads are the datapack-facing entry points for callers that only know a stored
 * value. Every state read and write goes through the profile the chain already is.</p>
 */
public final class CultivationService {
    private static final double PROGRESS_EPSILON = 1.0E-7D;

    private CultivationService() {
    }

    /**
     * Attempts the only legal next realm of the value's cultivation chain.
     */
    public static BreakthroughResult attempt(LivingEntity entity, CultivationAttachment spirit, ResourceHolderAttachment resources,
                                             Identifier resource, FormulaContext context, BooleanSupplier conditionsMet) {
        Reference<CultivationProfile> cultivation = CultivationProfiles.holder(entity.level().registryAccess(), resource).orElse(null);
        return cultivation == null ? BreakthroughResult.rejected(Failure.NO_NEXT_REALM, null)
                : attempt(entity, spirit, resources, cultivation, context, conditionsMet);
    }

    /**
     * Attempts the only legal next realm of one cultivation chain.
     */
    public static BreakthroughResult attempt(LivingEntity entity, CultivationAttachment spirit, ResourceHolderAttachment resources,
                                             Holder<CultivationProfile> cultivation, FormulaContext context, BooleanSupplier conditionsMet) {
        if (entity.level().isClientSide()) return BreakthroughResult.rejected(Failure.SERVER_ONLY, null);
        Transition transition = next(cultivation, spirit).orElse(null);
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
        if (!HolderHelper.id(target.cultivation()).equals(HolderHelper.id(transition.cultivation())))
            return BreakthroughResult.rejected(Failure.WRONG_RESOURCE, null);
        Holder<Resource> targetResource = transition.resource();
        FormulaContext resourceContext = ResourceService.formulaContext(entity, targetResource, context);
        Threshold threshold = threshold(transition, resourceContext);
        if (threshold == null) return BreakthroughResult.rejected(Failure.INVALID_FORMULA, null);
        double minimum = threshold.breakthroughExp();
        double maximum = threshold.maxExperience();
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum < 0.0D || maximum < 0.0D || minimum > maximum)
            return BreakthroughResult.rejected(Failure.INVALID_FORMULA, null);
        double progress = spirit.cultivationProgress(transition.cultivation());
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
            // TribulationService samples the aura influence itself so every phase is scaled by the
            // current environment, not only the phase that starts at breakthrough.
            target.tribulation().ifPresent(tribulation -> TribulationService.start(entity, entity.getData(MxtAttachments.TRIBULATION), tribulation, entity.level().getGameTime(), context));
            AbilityEventBridge.onBreakthrough(entity, targetId, context);
        } else {
            target.failAction().execute(entity, context);
        }
        return result;
    }

    private static BreakthroughResult commit(CultivationAttachment spirit, ResourceHolderAttachment resources, @NotNull Transition transition,
                                             FormulaContext context, BooleanSupplier conditionsMet, @NotNull IEventBus eventBus) {
        Holder<RealmStage> targetHolder = transition.target();
        RealmStage target = targetHolder.value();
        Threshold threshold = threshold(transition, context);
        if (threshold == null)
            return BreakthroughResult.rejected(Failure.INVALID_FORMULA, null);
        double minimum = threshold.breakthroughExp();
        Holder<CultivationProfile> cultivation = transition.cultivation();
        double progress = spirit.cultivationProgress(cultivation);
        if (progress < minimum)
            return BreakthroughResult.rejected(Failure.INSUFFICIENT_PROGRESS, null);
        if (!conditionsMet.getAsBoolean()) return BreakthroughResult.rejected(Failure.CONDITIONS, null);
        Evaluation costs;
        try {
            costs = ResourceTransactions.evaluate(target.breakthroughCosts(), context);
        } catch (IllegalArgumentException exception) {
            return BreakthroughResult.rejected(Failure.INVALID_FORMULA, null);
        }
        Pre event = new Pre(spirit, resources, targetHolder, context, minimum, costs.amounts());
        if (eventBus.post(event).isCanceled()) return BreakthroughResult.rejected(Failure.CANCELLED, null);
        Result payment = ResourceTransactions.tryConsume(resources, new Evaluation(event.costs()));
        if (!payment.committed())
            return BreakthroughResult.rejected(Failure.INSUFFICIENT_RESOURCE, payment.failedResource());
        spirit.setRealmStage(targetHolder);
        spirit.setCultivationProgress(cultivation, 0.0D);
        eventBus.post(new Post(spirit, resources, targetHolder, context, minimum, payment.amounts()));
        return BreakthroughResult.committed(payment.amounts());
    }

    /**
     * Resolves the pending transition of one cultivation chain. The chain is keyed by the profile
     * itself, so this reads the state entry directly instead of looking the value up again.
     */
    private static Optional<Transition> next(@Nullable Holder<CultivationProfile> cultivation, CultivationAttachment spirit) {
        if (cultivation == null) return Optional.empty();
        Identifier cultivationId = HolderHelper.id(cultivation);
        Holder<RealmStage> current = spirit.realmStage(cultivation);
        if (current != null) {
            return current.value().nextRealm().filter(value -> HolderHelper.id(value.value().cultivation()).equals(cultivationId))
                    .map(value -> Transition.realm(current, value, cultivation));
        }
        return cultivation.value().firstRealm()
                .filter(value -> HolderHelper.id(value.value().cultivation()).equals(cultivationId))
                .map(value -> Transition.mortal(cultivation, value));
    }

    /**
     * Adds cultivation progress while respecting the active transition's upper bound.
     */
    public static double addProgress(LivingEntity entity, Holder<Resource> resource, double amount, FormulaContext context) {
        Reference<CultivationProfile> cultivation = CultivationProfiles.holder(entity, resource).orElse(null);
        return cultivation == null ? 0.0D : addProgressForChain(entity, cultivation, amount, context);
    }

    public static double addProgressForChain(LivingEntity entity, Holder<CultivationProfile> cultivation, double amount, FormulaContext context) {
        if (!Double.isFinite(amount) || amount <= 0.0D) return 0.0D;
        CultivationAttachment spirit = entity.getData(MxtAttachments.CULTIVATION);
        Transition transition = next(cultivation, spirit).orElse(null);
        if (transition == null) return 0.0D;
        return addProgress(spirit, cultivation, amount, transition,
                ResourceService.formulaContext(entity, cultivation.value().resource(), context));
    }

    /**
     * Context-only variant used by server-side service paths without an entity reference.
     */
    public static double addProgress(CultivationAttachment spirit, Holder<Resource> resource, double amount, FormulaContext context) {
        return CultivationProfiles.holderServer(resource)
                .map(cultivation -> addProgressForChain(spirit, cultivation, amount, context)).orElse(0.0D);
    }

    public static double addProgressForChain(CultivationAttachment spirit, Holder<CultivationProfile> cultivation, double amount, FormulaContext context) {
        if (!Double.isFinite(amount) || amount <= 0.0D) return 0.0D;
        Transition transition = next(cultivation, spirit).orElse(null);
        return transition == null ? 0.0D : addProgress(spirit, cultivation, amount, transition, context);
    }

    private static double addProgress(CultivationAttachment spirit, Holder<CultivationProfile> cultivation, double amount,
                                      Transition transition, FormulaContext context) {
        Threshold threshold = threshold(transition, context);
        if (threshold == null) return 0.0D;
        double maximum = threshold.maxExperience();
        double before = spirit.cultivationProgress(cultivation);
        if (before >= maximum - PROGRESS_EPSILON) {
            if (Double.compare(before, maximum) != 0) spirit.setCultivationProgress(cultivation, maximum);
            return 0.0D;
        }
        double accepted = Math.max(0.0D, Math.min(amount, maximum - before));
        if (maximum - (before + accepted) <= PROGRESS_EPSILON) accepted = maximum - before;
        if (accepted > 0.0D) spirit.setCultivationProgress(cultivation, before + accepted);
        return accepted;
    }

    /**
     * Returns remaining legal progress for this chain's current transition.
     */
    public static double remainingProgressCapacity(CultivationAttachment spirit, Holder<Resource> resource, FormulaContext context) {
        return CultivationProfiles.holderServer(resource)
                .map(cultivation -> remainingProgressForChain(spirit, cultivation, context)).orElse(0.0D);
    }

    public static double remainingProgressForChain(CultivationAttachment spirit, Holder<CultivationProfile> cultivation, FormulaContext context) {
        Transition transition = next(cultivation, spirit).orElse(null);
        if (transition == null) return 0.0D;
        Threshold threshold = threshold(transition, context);
        if (threshold == null) return 0.0D;
        double maximum = threshold.maxExperience();
        double current = spirit.cultivationProgress(cultivation);
        double remaining = maximum - current;
        if (remaining >= 0.0D && remaining <= PROGRESS_EPSILON) {
            if (Double.compare(current, maximum) != 0) spirit.setCultivationProgress(cultivation, maximum);
            return 0.0D;
        }
        return remaining <= PROGRESS_EPSILON ? 0.0D : remaining;
    }

    /**
     * Resolves a chain's breakthrough state without mutating the player. This is shared by the
     * automatic-breakthrough tick and the information screen.
     */
    public static BreakthroughStatus breakthroughStatus(LivingEntity entity, Holder<Resource> resource, FormulaContext context) {
        Reference<CultivationProfile> cultivation = CultivationProfiles.holder(entity, resource).orElse(null);
        return cultivation == null ? BreakthroughStatus.UNAVAILABLE : breakthroughStatusForChain(entity, cultivation, context);
    }

    public static BreakthroughStatus breakthroughStatusForChain(LivingEntity entity, Holder<CultivationProfile> cultivation, FormulaContext context) {
        CultivationAttachment spirit = entity.getData(MxtAttachments.CULTIVATION);
        Transition transition = next(cultivation, spirit).orElse(null);
        if (transition == null) return BreakthroughStatus.UNAVAILABLE;
        Threshold threshold = threshold(transition, ResourceService.formulaContext(entity, transition.resource(), context));
        if (threshold == null) return BreakthroughStatus.UNAVAILABLE;
        double progress = spirit.cultivationProgress(cultivation);
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
        return CultivationProfiles.holder(entity, resource)
                .flatMap(cultivation -> pendingConditionsForChain(entity, cultivation));
    }

    public static Optional<CultivateConditions> pendingConditionsForChain(LivingEntity entity, Holder<CultivationProfile> cultivation) {
        Transition transition = next(cultivation, entity.getData(MxtAttachments.CULTIVATION)).orElse(null);
        return transition == null ? Optional.empty() : Optional.of(transition.conditions());
    }

    /**
     * Sets a validated realm administratively without traversing the registry at call time.
     */
    public static boolean setRealm(CultivationAttachment spirit, Identifier target) {
        ServerCache cache = ServerCache.get().orElse(null);
        Identifier cultivationId = cache == null ? null : cache.cultivationForRealm(target).orElse(null);
        if (cultivationId == null) return false;
        Reference<CultivationProfile> cultivation = MxtDatapackRegistries.holder(MxtResourceKeys.CULTIVATION, cultivationId).orElse(null);
        Holder<RealmStage> targetHolder = MxtDatapackRegistries.holder(MxtResourceKeys.REALM_STAGE, target).orElse(null);
        if (cultivation == null || targetHolder == null) return false;
        spirit.setRealmStage(targetHolder);
        spirit.setCultivationProgress(cultivation, 0.0D);
        return true;
    }

    private static Threshold threshold(Transition transition, FormulaContext context) {
        double breakthroughExp;
        double maxExperience;
        if (transition.mortal()) {
            breakthroughExp = transition.profile().startExp().evaluate(context);
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
                              @NotNull Holder<CultivationProfile> cultivation, @Nullable CultivationProfile profile,
                              boolean mortal) {
        private static Transition realm(Holder<RealmStage> current, Holder<RealmStage> target, Holder<CultivationProfile> cultivation) {
            return new Transition(current, target, cultivation, null, false);
        }

        private static Transition mortal(Holder<CultivationProfile> cultivation, Holder<RealmStage> target) {
            return new Transition(target, target, cultivation, cultivation.value(), true);
        }

        /**
         * The stored value this chain belongs to, as named by the profile the chain is keyed by.
         */
        private Holder<Resource> resource() {
            return this.cultivation.value().resource();
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
