package com.iafenvoy.mxt.runtime.ability;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.attachment.AbilityHolderData;
import com.iafenvoy.mxt.attachment.AbilityHolderData.Snapshot;
import com.iafenvoy.mxt.attachment.CurseHolderData;
import com.iafenvoy.mxt.attachment.ResourceHolderData;
import com.iafenvoy.mxt.data.ability.AbilityComponent;
import com.iafenvoy.mxt.data.ability.component.ChargesAbilityComponent;
import com.iafenvoy.mxt.data.ability.component.CooldownAbilityComponent;
import com.iafenvoy.mxt.data.ability.AbilityComponentState;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.type.ChannelledAbilityType;
import com.iafenvoy.mxt.data.ability.type.CompositeAbilityType;
import com.iafenvoy.mxt.data.ability.type.WordAbilityType;
import com.iafenvoy.mxt.data.ability.type.WordAbilityType.WordEffect;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.event.AbilityUseEvent;
import com.iafenvoy.mxt.event.CurseRemoveEvent.Reason;
import com.iafenvoy.mxt.event.ResourceConsumeEvent.Post;
import com.iafenvoy.mxt.event.ResourceConsumeEvent.Pre;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtCriteriaTriggers;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.cultivation.CultivationAffinity;
import com.iafenvoy.mxt.runtime.curse.CurseService;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Evaluation;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Result;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Server-side ability cost and cooldown transaction; actions are committed only after this service approves them.
 */
public final class AbilityService {
    private AbilityService() {
    }

    public static PrepareResult prepare(@NotNull Holder<Ability> ability, Ability definition, AbilityHolderData abilities,
                                        ResourceHolderData resources, long gameTime, FormulaContext context) {
        return prepare(ability, definition, abilities, resources, gameTime, context, null);
    }

    private static PrepareResult prepare(@NotNull Holder<Ability> ability, Ability definition, AbilityHolderData abilities,
                                         ResourceHolderData resources, long gameTime, FormulaContext context,
                                         LivingEntity payer) {
        if (!abilities.has(ability)) return PrepareResult.rejected(Failure.NOT_GRANTED, null);
        if (abilities.isOnCooldown(ability, gameTime)) return PrepareResult.rejected(Failure.COOLDOWN, null);
        double castTime = definition.castTime().evaluate(context);
        double cooldown = component(definition, CooldownAbilityComponent.class).map(component -> component.ticks().evaluate(context)).orElseGet(() -> definition.cooldown().evaluate(context));
        if (!Double.isFinite(castTime) || castTime < 0.0D || !Double.isFinite(cooldown) || cooldown < 0.0D) {
            return PrepareResult.rejected(Failure.INVALID_FORMULA, null);
        }
        long channelInterval = 0L;
        if (definition.type() instanceof ChannelledAbilityType channelled) {
            double interval = channelled.tickInterval().evaluate(context);
            if (!Double.isFinite(interval) || interval <= 0.0D || interval > Long.MAX_VALUE) {
                return PrepareResult.rejected(Failure.INVALID_FORMULA, null);
            }
            channelInterval = Math.max(1L, Math.round(interval));
        }
        Optional<ChargesAbilityComponent> charges = component(definition, ChargesAbilityComponent.class);
        double chargeBefore = Double.NaN;
        if (charges.isPresent()) {
            double maximum = charges.get().maximum().evaluate(context);
            double available = abilities.componentState(ability, "charges").map(AbilityComponentState::value).orElse(maximum);
            if (!Double.isFinite(maximum) || maximum < 1.0D || !Double.isFinite(available) || available < 1.0D) {
                return PrepareResult.rejected(Failure.NO_CHARGES, null);
            }
            chargeBefore = available;
        }
        Evaluation costs = payer == null ? ResourceTransactions.evaluate(definition.costs(), context)
                : ResourceTransactions.evaluate(payer, definition.costs(), context);
        Result preview = ResourceTransactions.tryConsume(copyOf(resources), costs);
        if (!preview.committed())
            return PrepareResult.rejected(Failure.INSUFFICIENT_RESOURCE, preview.failedResource());
        return PrepareResult.prepared(new PreparedUse(ability, costs, Math.round(castTime), Math.round(cooldown), channelInterval, charges.isPresent(), chargeBefore));
    }

    /**
     * Commits cost, component state and cooldown as one server-thread operation.
     */
    public static CommitResult commit(PreparedUse use, AbilityHolderData abilities, ResourceHolderData resources, long gameTime) {
        if (abilities.isOnCooldown(use.ability(), gameTime)) return CommitResult.rejected(Failure.COOLDOWN, null);
        Result payment = ResourceTransactions.tryConsume(resources, use.costs);
        if (!payment.committed()) return CommitResult.rejected(Failure.INSUFFICIENT_RESOURCE, payment.failedResource());
        abilities.setCooldownUntil(use.ability(), Math.addExact(gameTime, use.cooldownTicks));
        if (use.consumeCharge) {
            abilities.setComponentState(use.ability(), "charges", AbilityComponentState.initial(Math.max(0.0D, use.chargeBefore - 1.0D), gameTime));
        }
        return CommitResult.committed(payment.amounts());
    }

    /**
     * Canonical server-side path for immediate abilities. The resource transaction commits before
     * the action, preventing an action from taking effect when its declared costs cannot be paid.
     */
    public static UseResult use(Holder<Ability> ability, Ability definition, @NotNull Entity actor,
                                AbilityHolderData abilities, ResourceHolderData resources, long gameTime,
                                FormulaContext context) {
        if (actor instanceof LivingEntity living) {
            context = FormulaContexts.forEntity(living, context.variables());
            context = withElementAffinity(living, definition, context);
            if (!definition.elementAffinity().isEmpty() && context.value("element_modifier") <= 0.0D)
                return UseResult.rejected(Failure.ELEMENT_AFFINITY, null);
        }
        if (NeoForge.EVENT_BUS.post(new AbilityUseEvent.Pre(actor, HolderHelper.id(ability), definition, context)).isCanceled()) {
            return UseResult.rejected(Failure.CANCELLED, null);
        }
        if (!definition.condition().test(actor, context)) {
            return UseResult.rejected(Failure.CONDITION_FAILED, null);
        }
        if (!validateWord(definition, actor, context)) return UseResult.rejected(Failure.PERMISSION_DENIED, null);
        if (definition.type() instanceof CompositeAbilityType) {
            return useComposite(ability, definition, actor, abilities, resources, gameTime, context);
        }
        PrepareResult prepared = prepare(ability, definition, abilities, resources, gameTime, context,
                actor instanceof LivingEntity living ? living : null);
        if (!prepared.approved()) return UseResult.rejected(prepared.failure(), prepared.failedResource());
        if (prepared.use().castTimeTicks() > 0L) {
            abilities.setComponentState(ability, "cast_ends_at", AbilityComponentState.initial(Math.addExact(gameTime, prepared.use().castTimeTicks()), gameTime));
            return UseResult.castingResult();
        }
        return finishPreparedUse(prepared.use(), definition, actor, abilities, resources, gameTime, context);
    }

    /**
     * Completes a previously scheduled cast after the entity-tick bridge revalidates its definition.
     */
    public static UseResult finishCast(Holder<Ability> ability, Ability definition, Entity actor,
                                       AbilityHolderData abilities, ResourceHolderData resources, long gameTime,
                                       FormulaContext context) {
        if (actor instanceof LivingEntity living) {
            context = FormulaContexts.forEntity(living, context.variables());
            context = withElementAffinity(living, definition, context);
            if (!definition.elementAffinity().isEmpty() && context.value("element_modifier") <= 0.0D)
                return UseResult.rejected(Failure.ELEMENT_AFFINITY, null);
        }
        if (abilities.componentState(ability, "cast_ends_at").map(AbilityComponentState::value).orElse(Double.MAX_VALUE) > gameTime) {
            return UseResult.castingResult();
        }
        abilities.setComponentState(ability, "cast_ends_at", AbilityComponentState.initial(Double.MAX_VALUE, gameTime));
        if (!definition.condition().test(actor, context)) return UseResult.rejected(Failure.CONDITION_FAILED, null);
        if (!validateWord(definition, actor, context)) return UseResult.rejected(Failure.PERMISSION_DENIED, null);
        PrepareResult prepared = prepare(ability, definition, abilities, resources, gameTime, context,
                actor instanceof LivingEntity living ? living : null);
        if (!prepared.approved()) return UseResult.rejected(prepared.failure(), prepared.failedResource());
        return finishPreparedUse(prepared.use(), definition, actor, abilities, resources, gameTime, context);
    }

    private static UseResult finishPreparedUse(PreparedUse preparedUse, Ability definition, Entity actor,
                                               AbilityHolderData abilities, ResourceHolderData resources, long gameTime,
                                               FormulaContext context) {
        Pre resourceEvent = new Pre(resources, preparedUse.costs().amounts());
        if (NeoForge.EVENT_BUS.post(resourceEvent).isCanceled()) return UseResult.rejected(Failure.CANCELLED, null);
        PreparedUse adjustedUse = new PreparedUse(preparedUse.ability(), new Evaluation(resourceEvent.amounts()),
                preparedUse.castTimeTicks(), preparedUse.cooldownTicks(), preparedUse.channelIntervalTicks(), preparedUse.consumeCharge(), preparedUse.chargeBefore());
        CommitResult committed = commit(adjustedUse, abilities, resources, gameTime);
        if (!committed.committed()) return UseResult.rejected(committed.failure(), committed.failedResource());
        if (definition.type() instanceof ChannelledAbilityType) {
            abilities.setChannelledAbility(preparedUse.ability());
            abilities.setComponentState(preparedUse.ability(), "channel_next_tick", AbilityComponentState.initial(Math.addExact(gameTime, adjustedUse.channelIntervalTicks()), gameTime));
        } else if (definition.type() instanceof WordAbilityType word) {
            executeWord(word, actor, context);
        } else {
            definition.entityAction().execute(actor, context);
        }
        NeoForge.EVENT_BUS.post(new Post(resources, committed.amounts()));
        NeoForge.EVENT_BUS.post(new AbilityUseEvent.Post(actor, HolderHelper.id(preparedUse.ability()), definition, context, committed.amounts()));
        if (actor instanceof ServerPlayer player)
            MxtCriteriaTriggers.ABILITY.get().trigger(player, HolderHelper.id(preparedUse.ability()));
        return UseResult.committed(committed.amounts());
    }

    /**
     * Runs at most one upkeep pulse. Call this only from the server entity tick bridge.
     */
    public static ChannelResult tickChannel(Holder<Ability> ability, Ability definition, Entity actor,
                                            AbilityHolderData abilities, ResourceHolderData resources, long gameTime,
                                            FormulaContext context) {
        if (actor instanceof LivingEntity living) {
            context = withElementAffinity(living, definition, FormulaContexts.forEntity(living, context.variables()));
            if (!definition.elementAffinity().isEmpty() && context.value("element_modifier") <= 0.0D) {
                stopChannel(abilities);
                return ChannelResult.stopped(Failure.ELEMENT_AFFINITY);
            }
        }
        if (abilities.channelledAbility().filter(ability::equals).isEmpty()) return ChannelResult.inactive();
        if (!abilities.has(ability) || !(definition.type() instanceof ChannelledAbilityType(
                NumberProvider tickInterval,
                List<ResourceCost> upkeepCosts
        ))) {
            stopChannel(abilities);
            return ChannelResult.stopped(Failure.NOT_GRANTED);
        }
        long nextTick = Math.round(abilities.componentState(ability, "channel_next_tick").map(AbilityComponentState::value).orElse((double) gameTime));
        if (gameTime < nextTick) return ChannelResult.waiting(nextTick);
        if (!definition.condition().test(actor, context)) {
            stopChannel(abilities);
            return ChannelResult.stopped(Failure.CONDITION_FAILED);
        }
        double interval = tickInterval.evaluate(context);
        if (!Double.isFinite(interval) || interval <= 0.0D || interval > Long.MAX_VALUE) {
            stopChannel(abilities);
            return ChannelResult.stopped(Failure.INVALID_FORMULA);
        }
        Evaluation upkeep = actor instanceof LivingEntity living ? ResourceTransactions.evaluate(living, upkeepCosts, context)
                : ResourceTransactions.evaluate(upkeepCosts, context);
        Pre resourceEvent = new Pre(resources, upkeep.amounts());
        if (NeoForge.EVENT_BUS.post(resourceEvent).isCanceled()) {
            stopChannel(abilities);
            return ChannelResult.stopped(Failure.CANCELLED);
        }
        Result payment = ResourceTransactions.tryConsume(resources, new Evaluation(resourceEvent.amounts()));
        if (!payment.committed()) {
            stopChannel(abilities);
            return ChannelResult.stopped(Failure.INSUFFICIENT_RESOURCE);
        }
        definition.entityAction().execute(actor, context);
        NeoForge.EVENT_BUS.post(new Post(resources, payment.amounts()));
        long intervalTicks = Math.max(1L, Math.round(interval));
        long followingTick = Math.addExact(gameTime, intervalTicks);
        abilities.setComponentState(ability, "channel_next_tick", AbilityComponentState.initial(followingTick, gameTime));
        return ChannelResult.pulsed(followingTick, payment.amounts());
    }

    public static boolean stopChannel(AbilityHolderData abilities) {
        if (abilities.channelledAbility().isEmpty()) return false;
        abilities.setChannelledAbility(null);
        return true;
    }

    /**
     * Clears a pending cast without touching resources, cooldowns or unrelated component state.
     */
    public static boolean cancelCast(Holder<Ability> ability, AbilityHolderData abilities, long gameTime) {
        double endsAt = abilities.componentState(ability, "cast_ends_at").map(AbilityComponentState::value).orElse(Double.MAX_VALUE);
        if (endsAt == Double.MAX_VALUE) return false;
        abilities.setComponentState(ability, "cast_ends_at", AbilityComponentState.initial(Double.MAX_VALUE, gameTime));
        return true;
    }

    private static boolean validateWord(Ability definition, Entity actor, FormulaContext context) {
        if (!(definition.type() instanceof WordAbilityType(
                WordEffect effect, boolean requiresOperator,
                NumberProvider amount1
        ))) return true;
        if (requiresOperator && (!(actor instanceof ServerPlayer player) || !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)))
            return false;
        if (effect != WordAbilityType.WordEffect.SELF_HEAL) return true;
        try {
            double amount = amount1.evaluate(context);
            return Double.isFinite(amount) && amount >= 0.0D && amount <= Float.MAX_VALUE;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static void executeWord(WordAbilityType word, Entity actor, FormulaContext context) {
        if (word.effect() == WordAbilityType.WordEffect.SELF_HEAL && actor instanceof LivingEntity living) {
            living.heal((float) word.amount().evaluate(context));
        } else if (word.effect() == WordAbilityType.WordEffect.PURGE_SELF_CURSES) {
            CurseHolderData holder = actor.getData(MxtAttachments.CURSE_HOLDER);
            new LinkedList<>(holder.instances().keySet()).forEach(curse ->
                    CurseService.remove(holder, curse, Reason.EXPLICIT, -1L));
        }
    }

    /**
     * Executes a composite as one transaction: every child must commit or all state is restored.
     */
    public static UseResult useComposite(Holder<Ability> composite, Ability compositeDefinition, Entity actor,
                                         AbilityHolderData abilities, ResourceHolderData resources, long gameTime,
                                         FormulaContext context) {
        if (!(compositeDefinition.type() instanceof CompositeAbilityType(List<Holder<Ability>> abilities1, boolean allRequired)))
            return UseResult.rejected(Failure.INVALID_FORMULA, null);
        Snapshot abilitySnapshot = abilities.snapshot();
        ResourceHolderData.Snapshot resourceSnapshot = resources.snapshot();
        LinkedHashMap<Identifier, Double> paid = new LinkedHashMap<>();
        UseResult lastFailure = UseResult.rejected(Failure.NOT_GRANTED, null);
        for (Holder<Ability> childHolder : abilities1) {
            Ability child = childHolder.value();
            UseResult result = use(childHolder, child, actor, abilities, resources, gameTime, context);
            if (!result.committed() && !result.casting()) {
                lastFailure = result;
                if (allRequired) {
                    abilities.restore(abilitySnapshot);
                    resources.restore(resourceSnapshot);
                    return UseResult.rejected(result.failure() == null ? Failure.CANCELLED : result.failure(), result.failedResource());
                }
                abilities.restore(abilitySnapshot);
                resources.restore(resourceSnapshot);
            }
            paid.putAll(result.amounts());
            if (!allRequired) return result;
        }
        return allRequired ? UseResult.committed(paid) : lastFailure;
    }

    private static ResourceHolderData copyOf(ResourceHolderData source) {
        ResourceHolderData copy = new ResourceHolderData();
        source.values().forEach(copy::set);
        return copy;
    }

    private static FormulaContext withElementAffinity(LivingEntity actor, Ability definition, FormulaContext context) {
        if (definition.elementAffinity().isEmpty()) return context;
        double modifier = CultivationAffinity.abilityMultiplier(actor.getData(MxtAttachments.SPIRIT_DATA), definition.elementAffinity(), context,
                id -> MxtDatapackRegistries.get(MxtResourceKeys.SPIRIT_ROOT, id));
        LinkedHashMap<String, Double> variables = new LinkedHashMap<>(context.variables());
        variables.put("element_modifier", modifier);
        return new FormulaContext(variables, context.random());
    }

    private static <T extends AbilityComponent> Optional<T> component(Ability definition, Class<T> type) {
        return definition.components().stream().filter(type::isInstance).map(type::cast).findFirst();
    }

    public enum Failure {DISABLED, NOT_GRANTED, COOLDOWN, INSUFFICIENT_RESOURCE, INVALID_FORMULA, CONDITION_FAILED, NO_CHARGES, CANCELLED, PERMISSION_DENIED, ELEMENT_AFFINITY, SERVER_ONLY}

    public record PreparedUse(Holder<Ability> ability, Evaluation costs, long castTimeTicks,
                              long cooldownTicks, long channelIntervalTicks,
                              boolean consumeCharge, double chargeBefore) {
    }

    public record PrepareResult(PreparedUse use, Failure failure, Identifier failedResource) {
        private static PrepareResult prepared(PreparedUse use) {
            return new PrepareResult(use, null, null);
        }

        private static PrepareResult rejected(Failure failure, Identifier resource) {
            return new PrepareResult(null, failure, resource);
        }

        public boolean approved() {
            return this.use != null;
        }
    }

    public record CommitResult(boolean committed, Failure failure, Identifier failedResource,
                               Map<Identifier, Double> amounts) {
        private static CommitResult committed(Map<Identifier, Double> amounts) {
            return new CommitResult(true, null, null, amounts);
        }

        private static CommitResult rejected(Failure failure, Identifier resource) {
            return new CommitResult(false, failure, resource, Map.of());
        }
    }

    public record UseResult(boolean committed, boolean casting, Failure failure, Identifier failedResource,
                            Map<Identifier, Double> amounts) {
        private static UseResult committed(Map<Identifier, Double> amounts) {
            return new UseResult(true, false, null, null, amounts);
        }

        private static UseResult castingResult() {
            return new UseResult(false, true, null, null, Map.of());
        }

        private static UseResult rejected(Failure failure, Identifier resource) {
            return new UseResult(false, false, failure, resource, Map.of());
        }
    }

    public record ChannelResult(State state, Failure failure, long nextTick,
                                Map<Identifier, Double> amounts) {
        private static ChannelResult inactive() {
            return new ChannelResult(State.INACTIVE, null, -1L, Map.of());
        }

        private static ChannelResult waiting(long nextTick) {
            return new ChannelResult(State.WAITING, null, nextTick, Map.of());
        }

        private static ChannelResult pulsed(long nextTick, Map<Identifier, Double> amounts) {
            return new ChannelResult(State.PULSED, null, nextTick, amounts);
        }

        private static ChannelResult stopped(Failure failure) {
            return new ChannelResult(State.STOPPED, failure, -1L, Map.of());
        }
    }

    public enum State {INACTIVE, WAITING, PULSED, STOPPED}
}
