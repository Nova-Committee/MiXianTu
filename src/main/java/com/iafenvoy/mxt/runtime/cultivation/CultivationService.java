package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.ResourceHolderData;
import com.iafenvoy.mxt.attachment.SpiritData;
import com.iafenvoy.mxt.data.cultivation.RealmStageDefinition;
import com.iafenvoy.mxt.data.resource.ResourceDefinition;
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
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Evaluation;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Result;
import com.iafenvoy.mxt.runtime.tribulation.TribulationService;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Server-side breakthrough transaction. Content conditions are evaluated by callers before payment is committed.
 */
public final class CultivationService {
    private CultivationService() {
    }

    /**
     * Attempts the only legal next realm in the active resource's linear chain.
     */
    public static BreakthroughResult attempt(LivingEntity entity, SpiritData spirit, ResourceHolderData resources,
                                             Identifier resource, FormulaContext context, BooleanSupplier conditionsMet) {
        if (entity.level().isClientSide()) return BreakthroughResult.rejected(Failure.SERVER_ONLY, null);
        if (spirit.realmStage().isPresent() && ServerCache.get()
                .flatMap(cache -> cache.resourceForRealm(spirit.realmStage().orElseThrow()))
                .filter(resource::equals).isEmpty()) return BreakthroughResult.rejected(Failure.NO_NEXT_REALM, null);
        Next next = next(spirit, resource).orElse(null);
        if (next == null) return BreakthroughResult.rejected(Failure.NO_NEXT_REALM, null);
        return attempt(entity, spirit, resources, next.id(), next.definition(), context, conditionsMet);
    }

    /**
     * Entity-aware breakthrough entry point. It preserves the same transaction semantics and
     * dispatches triggered abilities only after the realm state has committed.
     */
    private static BreakthroughResult attempt(LivingEntity entity, SpiritData spirit, ResourceHolderData resources, Identifier targetId,
                                              RealmStageDefinition target, FormulaContext context, BooleanSupplier conditionsMet) {
        if (!target.resource().equals(activeResource(spirit, targetId, target)))
            return BreakthroughResult.rejected(Failure.WRONG_RESOURCE, null);
        boolean configuredConditions = target.upgradeConditions().stream().allMatch(condition -> condition.test(entity, context));
        boolean requiredAbilities = RegistryCodecs.resolve(target.abilityRequirements(), MxtDatapackRegistries.registry(MxtRegistryKeys.ABILITY))
                .map(HolderHelper::id)
                .allMatch(ability -> entity.getData(MxtAttachments.ABILITY_HOLDER).has(ability));
        BreakthroughResult result = commit(spirit, resources, targetId, target, context, () -> configuredConditions && requiredAbilities && conditionsMet.getAsBoolean(), NeoForge.EVENT_BUS);
        if (result.advanced()) {
            if (entity instanceof ServerPlayer player) MxtCriteriaTriggers.BREAKTHROUGH.get().trigger(player, targetId);
            DomainBehaviorService.execute(MxtTypeRegistries.CULTIVATION_OUTCOME_BEHAVIOR, target.successBehavior(), BehaviorContext.of(
                    Kind.BREAKTHROUGH_SUCCESS, targetId, entity, context, true));
            FormulaContext tribulationContext = context.with("aura_tribulation_modifier", AuraService.getPositionAura(entity.level(), entity.blockPosition()).rules().tribulationModify());
            target.tribulation().ifPresent(tribulation ->
                    TribulationService.start(entity, entity.getData(MxtAttachments.TRIBULATION), HolderHelper.id(tribulation), tribulation.value(),
                            entity.level().getGameTime(), tribulationContext));
            AbilityEventBridge.onBreakthrough(entity, targetId, context);
        } else {
            DomainBehaviorService.execute(MxtTypeRegistries.CULTIVATION_OUTCOME_BEHAVIOR, target.failBehavior(), BehaviorContext.of(
                    Kind.BREAKTHROUGH_FAILURE, targetId, entity, context, false));
        }
        return result;
    }

    private static BreakthroughResult commit(SpiritData spirit, ResourceHolderData resources, @NotNull Identifier targetId,
                                             RealmStageDefinition target, FormulaContext context, BooleanSupplier conditionsMet, @NotNull IEventBus eventBus) {
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

    private static Optional<Next> next(SpiritData spirit, Identifier resource) {
        Identifier current = spirit.realmStage().orElse(null);
        if (current != null) {
            RealmStageDefinition definition = MxtDatapackRegistries.get(MxtDatapackRegistries.REALM_STAGE, current).orElse(null);
            if (definition == null || !HolderHelper.id(definition.resource()).equals(resource)) return Optional.empty();
            return definition.nextRealm().filter(value -> HolderHelper.id(value.value().resource()).equals(resource))
                    .map(value -> new Next(HolderHelper.id(value), value.value()));
        }
        ResourceDefinition definition = MxtDatapackRegistries.get(MxtDatapackRegistries.RESOURCE, resource).orElse(null);
        return definition == null ? Optional.empty() : definition.firstRealm()
                .filter(value -> HolderHelper.id(value.value().resource()).equals(resource))
                .map(value -> new Next(HolderHelper.id(value), value.value()));
    }

    /**
     * Sets a validated realm administratively without traversing the registry at call time.
     */
    public static boolean setRealm(SpiritData spirit, Identifier target) {
        ServerCache cache = ServerCache.get().orElse(null);
        Identifier resource = cache == null ? null : cache.resourceForRealm(target).orElse(null);
        if (resource == null) return false;
        Identifier current = spirit.realmStage().orElse(null);
        if (current != null && !cache.resourceForRealm(current).filter(resource::equals).isPresent()) return false;
        spirit.setRealmStage(target);
        spirit.setCultivationProgress(0.0D);
        return true;
    }

    private static Identifier activeResource(SpiritData spirit, Identifier targetId, RealmStageDefinition target) {
        return spirit.realmStage().flatMap(id -> MxtDatapackRegistries.get(MxtDatapackRegistries.REALM_STAGE, id))
                .map(RealmStageDefinition::resource).map(HolderHelper::id).orElseGet(() -> HolderHelper.id(target.resource()));
    }

    private record Next(Identifier id, RealmStageDefinition definition) {
    }

    public enum Failure {DISABLED, WRONG_RESOURCE, NO_NEXT_REALM, INSUFFICIENT_PROGRESS, CONDITIONS, INSUFFICIENT_RESOURCE, INVALID_FORMULA, CANCELLED, SERVER_ONLY}

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
