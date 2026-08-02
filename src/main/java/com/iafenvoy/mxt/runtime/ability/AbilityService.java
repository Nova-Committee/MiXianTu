package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.attachment.AbilityHolderData;
import com.iafenvoy.mxt.attachment.AbilityHolderData.Snapshot;
import com.iafenvoy.mxt.attachment.CurseHolderData;
import com.iafenvoy.mxt.attachment.ResourceHolderData;
import com.iafenvoy.mxt.data.ability.AbilityComponent;
import com.iafenvoy.mxt.data.ability.AbilityComponent.Charges;
import com.iafenvoy.mxt.data.ability.AbilityComponent.Cooldown;
import com.iafenvoy.mxt.data.ability.AbilityComponentState;
import com.iafenvoy.mxt.data.ability.AbilityDefinition;
import com.iafenvoy.mxt.data.ability.AbilityType.Channelled;
import com.iafenvoy.mxt.data.ability.AbilityType.Composite;
import com.iafenvoy.mxt.data.ability.AbilityType.Word;
import com.iafenvoy.mxt.data.ability.AbilityType.WordEffect;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.event.AbilityUseEvent;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Server-side ability cost and cooldown transaction; actions are committed only after this service approves them.
 */
public final class AbilityService {
    private AbilityService() {
    }

    public static PrepareResult prepare(@NotNull Identifier abilityId, AbilityDefinition definition, AbilityHolderData abilities,
                                        ResourceHolderData resources, long gameTime, FormulaContext context) {
        if (!abilities.has(abilityId)) return PrepareResult.rejected(Failure.NOT_GRANTED, null);
        if (abilities.isOnCooldown(abilityId, gameTime)) return PrepareResult.rejected(Failure.COOLDOWN, null);
        double castTime = definition.castTime().evaluate(context);
        double cooldown = component(definition, Cooldown.class).map(component -> component.ticks().evaluate(context)).orElseGet(() -> definition.cooldown().evaluate(context));
        if (!Double.isFinite(castTime) || castTime < 0.0D || !Double.isFinite(cooldown) || cooldown < 0.0D) {
            return PrepareResult.rejected(Failure.INVALID_FORMULA, null);
        }
        long channelInterval = 0L;
        if (definition.typedType() instanceof Channelled channelled) {
            double interval = channelled.tickInterval().evaluate(context);
            if (!Double.isFinite(interval) || interval <= 0.0D || interval > Long.MAX_VALUE) {
                return PrepareResult.rejected(Failure.INVALID_FORMULA, null);
            }
            channelInterval = Math.max(1L, Math.round(interval));
        }
        Optional<Charges> charges = component(definition, Charges.class);
        double chargeBefore = Double.NaN;
        if (charges.isPresent()) {
            double maximum = charges.get().maximum().evaluate(context);
            double available = abilities.componentState(abilityId, "charges").map(AbilityComponentState::value).orElse(maximum);
            if (!Double.isFinite(maximum) || maximum < 1.0D || !Double.isFinite(available) || available < 1.0D) {
                return PrepareResult.rejected(Failure.NO_CHARGES, null);
            }
            chargeBefore = available;
        }
        Evaluation costs = ResourceTransactions.evaluate(definition.costs(), context);
        Result preview = ResourceTransactions.tryConsume(copyOf(resources), costs);
        if (!preview.committed())
            return PrepareResult.rejected(Failure.INSUFFICIENT_RESOURCE, preview.failedResource());
        return PrepareResult.prepared(new PreparedUse(abilityId, costs, Math.round(castTime), Math.round(cooldown), channelInterval, charges.isPresent(), chargeBefore));
    }

    /**
     * Commits cost, component state and cooldown as one server-thread operation.
     */
    public static CommitResult commit(PreparedUse use, AbilityHolderData abilities, ResourceHolderData resources, long gameTime) {
        if (abilities.isOnCooldown(use.abilityId, gameTime)) return CommitResult.rejected(Failure.COOLDOWN, null);
        Result payment = ResourceTransactions.tryConsume(resources, use.costs);
        if (!payment.committed()) return CommitResult.rejected(Failure.INSUFFICIENT_RESOURCE, payment.failedResource());
        abilities.setCooldownUntil(use.abilityId, Math.addExact(gameTime, use.cooldownTicks));
        if (use.consumeCharge) {
            abilities.setComponentState(use.abilityId, "charges", AbilityComponentState.initial(Math.max(0.0D, use.chargeBefore - 1.0D), gameTime));
        }
        return CommitResult.committed(payment.amounts());
    }

    /**
     * Canonical server-side path for immediate abilities. The resource transaction commits before
     * the action, preventing an action from taking effect when its declared costs cannot be paid.
     */
    public static UseResult use(Identifier abilityId, AbilityDefinition definition, @NotNull Entity actor,
                                AbilityHolderData abilities, ResourceHolderData resources, long gameTime,
                                FormulaContext context) {
        if (actor instanceof LivingEntity living) {
            context = FormulaContexts.forEntity(living, context.variables());
            context = withElementAffinity(living, definition, context);
            if (!definition.elementAffinity().isEmpty() && context.value("element_modifier") <= 0.0D)
                return UseResult.rejected(Failure.ELEMENT_AFFINITY, null);
        }
        if (NeoForge.EVENT_BUS.post(new AbilityUseEvent.Pre(actor, abilityId, definition, context)).isCanceled()) {
            return UseResult.rejected(Failure.CANCELLED, null);
        }
        if (!definition.condition().test(actor, context)) {
            return UseResult.rejected(Failure.CONDITION_FAILED, null);
        }
        if (!validateWord(definition, actor, context)) return UseResult.rejected(Failure.PERMISSION_DENIED, null);
        if (definition.typedType() instanceof Composite) {
            return useComposite(abilityId, definition, actor, abilities, resources, gameTime, context, id -> MxtDatapackRegistries.get(MxtDatapackRegistries.ABILITY, id));
        }
        PrepareResult prepared = prepare(abilityId, definition, abilities, resources, gameTime, context);
        if (!prepared.approved()) return UseResult.rejected(prepared.failure(), prepared.failedResource());
        if (prepared.use().castTimeTicks() > 0L) {
            abilities.setComponentState(abilityId, "cast_ends_at", AbilityComponentState.initial(Math.addExact(gameTime, prepared.use().castTimeTicks()), gameTime));
            return UseResult.castingResult();
        }
        return finishPreparedUse(prepared.use(), definition, actor, abilities, resources, gameTime, context);
    }

    /**
     * Completes a previously scheduled cast after the entity-tick bridge revalidates its definition.
     */
    public static UseResult finishCast(Identifier abilityId, AbilityDefinition definition, Entity actor,
                                       AbilityHolderData abilities, ResourceHolderData resources, long gameTime,
                                       FormulaContext context) {
        if (actor instanceof LivingEntity living) {
            context = FormulaContexts.forEntity(living, context.variables());
            context = withElementAffinity(living, definition, context);
            if (!definition.elementAffinity().isEmpty() && context.value("element_modifier") <= 0.0D)
                return UseResult.rejected(Failure.ELEMENT_AFFINITY, null);
        }
        if (abilities.componentState(abilityId, "cast_ends_at").map(AbilityComponentState::value).orElse(Double.MAX_VALUE) > gameTime) {
            return UseResult.castingResult();
        }
        abilities.setComponentState(abilityId, "cast_ends_at", AbilityComponentState.initial(Double.MAX_VALUE, gameTime));
        if (!definition.condition().test(actor, context)) return UseResult.rejected(Failure.CONDITION_FAILED, null);
        if (!validateWord(definition, actor, context)) return UseResult.rejected(Failure.PERMISSION_DENIED, null);
        PrepareResult prepared = prepare(abilityId, definition, abilities, resources, gameTime, context);
        if (!prepared.approved()) return UseResult.rejected(prepared.failure(), prepared.failedResource());
        return finishPreparedUse(prepared.use(), definition, actor, abilities, resources, gameTime, context);
    }

    private static UseResult finishPreparedUse(PreparedUse preparedUse, AbilityDefinition definition, Entity actor,
                                               AbilityHolderData abilities, ResourceHolderData resources, long gameTime,
                                               FormulaContext context) {
        Pre resourceEvent = new Pre(resources, preparedUse.costs().amounts());
        if (NeoForge.EVENT_BUS.post(resourceEvent).isCanceled()) return UseResult.rejected(Failure.CANCELLED, null);
        PreparedUse adjustedUse = new PreparedUse(preparedUse.abilityId(), new Evaluation(resourceEvent.amounts()),
                preparedUse.castTimeTicks(), preparedUse.cooldownTicks(), preparedUse.channelIntervalTicks(), preparedUse.consumeCharge(), preparedUse.chargeBefore());
        CommitResult committed = commit(adjustedUse, abilities, resources, gameTime);
        if (!committed.committed()) return UseResult.rejected(committed.failure(), committed.failedResource());
        if (definition.typedType() instanceof Channelled) {
            abilities.setChannelledAbility(preparedUse.abilityId());
            abilities.setComponentState(preparedUse.abilityId(), "channel_next_tick", AbilityComponentState.initial(Math.addExact(gameTime, adjustedUse.channelIntervalTicks()), gameTime));
        } else if (definition.typedType() instanceof Word word) {
            executeWord(word, actor, context);
        } else {
            definition.entityAction().execute(actor, context);
        }
        NeoForge.EVENT_BUS.post(new Post(resources, committed.amounts()));
        NeoForge.EVENT_BUS.post(new AbilityUseEvent.Post(actor, preparedUse.abilityId(), definition, context, committed.amounts()));
        if (actor instanceof ServerPlayer player)
            MxtCriteriaTriggers.ABILITY.get().trigger(player, preparedUse.abilityId());
        return UseResult.committed(committed.amounts());
    }

    /**
     * Runs at most one upkeep pulse. Call this only from the server entity tick bridge.
     */
    public static ChannelResult tickChannel(Identifier abilityId, AbilityDefinition definition, Entity actor,
                                            AbilityHolderData abilities, ResourceHolderData resources, long gameTime,
                                            FormulaContext context) {
        if (actor instanceof LivingEntity living) {
            context = withElementAffinity(living, definition, FormulaContexts.forEntity(living, context.variables()));
            if (!definition.elementAffinity().isEmpty() && context.value("element_modifier") <= 0.0D) {
                stopChannel(abilities);
                return ChannelResult.stopped(Failure.ELEMENT_AFFINITY);
            }
        }
        if (abilities.channelledAbility().filter(abilityId::equals).isEmpty()) return ChannelResult.inactive();
        if (!abilities.has(abilityId) || !(definition.typedType() instanceof Channelled(
                NumberProvider tickInterval,
                List<ResourceCost> upkeepCosts
        ))) {
            stopChannel(abilities);
            return ChannelResult.stopped(Failure.NOT_GRANTED);
        }
        long nextTick = Math.round(abilities.componentState(abilityId, "channel_next_tick").map(AbilityComponentState::value).orElse((double) gameTime));
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
        Evaluation upkeep = ResourceTransactions.evaluate(upkeepCosts, context);
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
        abilities.setComponentState(abilityId, "channel_next_tick", AbilityComponentState.initial(followingTick, gameTime));
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
    public static boolean cancelCast(Identifier abilityId, AbilityHolderData abilities, long gameTime) {
        double endsAt = abilities.componentState(abilityId, "cast_ends_at").map(AbilityComponentState::value).orElse(Double.MAX_VALUE);
        if (endsAt == Double.MAX_VALUE) return false;
        abilities.setComponentState(abilityId, "cast_ends_at", AbilityComponentState.initial(Double.MAX_VALUE, gameTime));
        return true;
    }

    private static boolean validateWord(AbilityDefinition definition, Entity actor, FormulaContext context) {
        if (!(definition.typedType() instanceof Word(
                WordEffect effect, boolean requiresOperator,
                NumberProvider amount1
        ))) return true;
        if (requiresOperator && (!(actor instanceof ServerPlayer player) || !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)))
            return false;
        if (effect != WordEffect.SELF_HEAL) return true;
        try {
            double amount = amount1.evaluate(context);
            return Double.isFinite(amount) && amount >= 0.0D && amount <= Float.MAX_VALUE;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static void executeWord(Word word, Entity actor, FormulaContext context) {
        if (word.effect() == WordEffect.SELF_HEAL && actor instanceof LivingEntity living) {
            living.heal((float) word.amount().evaluate(context));
        } else if (word.effect() == WordEffect.PURGE_SELF_CURSES) {
            CurseHolderData holder = actor.getData(MxtAttachments.CURSE_HOLDER);
            List.copyOf(holder.instances().keySet()).forEach(id -> CurseService.remove(holder, id));
        }
    }

    /**
     * Executes a composite as one transaction: every child must commit or all state is restored.
     */
    public static UseResult useComposite(Identifier compositeId, AbilityDefinition composite, Entity actor,
                                         AbilityHolderData abilities, ResourceHolderData resources, long gameTime,
                                         FormulaContext context, Function<Identifier, Optional<AbilityDefinition>> definitions) {
        if (!(composite.typedType() instanceof Composite(
                List<Holder<AbilityDefinition>> abilities1, boolean allRequired
        )))
            return UseResult.rejected(Failure.INVALID_FORMULA, null);
        Snapshot abilitySnapshot = abilities.snapshot();
        Map<Identifier, Double> resourceSnapshot = resources.values();
        LinkedHashMap<Identifier, Double> paid = new LinkedHashMap<>();
        UseResult lastFailure = UseResult.rejected(Failure.NOT_GRANTED, null);
        for (Holder<AbilityDefinition> childHolder : abilities1) {
            Identifier childId = HolderHelper.id(childHolder);
            AbilityDefinition child = childHolder.value();
            UseResult result = use(childId, child, actor, abilities, resources, gameTime, context);
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

    private static FormulaContext withElementAffinity(LivingEntity actor, AbilityDefinition definition, FormulaContext context) {
        if (definition.elementAffinity().isEmpty()) return context;
        double modifier = CultivationAffinity.abilityMultiplier(actor.getData(MxtAttachments.SPIRIT_DATA), definition.elementAffinity(), context,
                id -> MxtDatapackRegistries.get(MxtDatapackRegistries.SPIRIT_ROOT, id));
        LinkedHashMap<String, Double> variables = new LinkedHashMap<>(context.variables());
        variables.put("element_modifier", modifier);
        return new FormulaContext(variables);
    }

    private static <T extends AbilityComponent> Optional<T> component(AbilityDefinition definition, Class<T> type) {
        return definition.components().stream().filter(type::isInstance).map(type::cast).findFirst();
    }

    public enum Failure {DISABLED, NOT_GRANTED, COOLDOWN, INSUFFICIENT_RESOURCE, INVALID_FORMULA, CONDITION_FAILED, NO_CHARGES, CANCELLED, PERMISSION_DENIED, ELEMENT_AFFINITY, SERVER_ONLY}

    public record PreparedUse(Identifier abilityId, Evaluation costs, long castTimeTicks,
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
