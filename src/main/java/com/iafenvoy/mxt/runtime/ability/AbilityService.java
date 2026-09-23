package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.CurseHolderAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.ToggleContext;
import com.iafenvoy.mxt.data.ability.type.ChannelledAbilityType;
import com.iafenvoy.mxt.data.ability.type.CompositeAbilityType;
import com.iafenvoy.mxt.data.ability.type.WordAbilityType;
import com.iafenvoy.mxt.data.ability.type.WordAbilityType.WordEffect;
import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.CostTransaction;
import com.iafenvoy.mxt.data.cost.ItemCostDraft;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostFailure;
import com.iafenvoy.mxt.data.cost.context.CostOrigin;
import com.iafenvoy.mxt.data.storage.DataStorage;
import com.iafenvoy.mxt.data.storage.builtin.ChargesDataStorage;
import com.iafenvoy.mxt.data.storage.builtin.CooldownDataStorage;
import com.iafenvoy.mxt.data.storage.runtime.CastDeadline;
import com.iafenvoy.mxt.data.storage.runtime.ChannelPulse;
import com.iafenvoy.mxt.event.AbilityUseEvent;
import com.iafenvoy.mxt.event.CurseRemoveEvent.Reason;
import com.iafenvoy.mxt.event.ResourceConsumeEvent.Post;
import com.iafenvoy.mxt.event.ResourceConsumeEvent.Pre;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtCriteriaTriggers;
import com.iafenvoy.mxt.runtime.cultivation.CultivationAffinity;
import com.iafenvoy.mxt.runtime.cultivation.SkillStageService;
import com.iafenvoy.mxt.runtime.curse.CurseService;
import com.iafenvoy.mxt.runtime.damage.DamageCalculationService;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Evaluation;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Result;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Server-side ability cost and cooldown transaction: actions are committed only after this service approves
 * them, as one server-thread operation. World actions are deliberately never rolled back, which is why every
 * child of a composite is validated first and paid before any of them runs.
 *
 * <p>Everything here takes the ability as its registry holder: however it was granted - a book, a command, a
 * script or a carried artifact - it takes the same path, and the holder's id is what the grant ledger, the
 * cooldowns and the stored state are keyed by.
 */
public final class AbilityService {
    private AbilityService() {
    }

    private static PrepareResult prepare(Holder<Ability> ability, AbilityAttachment abilities, ResourceHolderAttachment resources, long gameTime,
                                         FormulaContext context, LivingEntity payer, boolean requiresGrant, @Nullable ItemCostDraft itemDraft) {
        Ability definition = ability.value();
        if (requiresGrant && !abilities.has(HolderHelper.id(ability))) return PrepareResult.rejected(Failure.NOT_GRANTED, null);
        if (abilities.isOnCooldown(HolderHelper.id(ability), gameTime)) return PrepareResult.rejected(Failure.COOLDOWN, null);
        double castTime = definition.castTime().evaluate(context);
        double cooldown = cooldownOf(ability, abilities, context);
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
        Optional<ChargesDataStorage> charges = kind(definition, ChargesDataStorage.class);
        double chargeBefore = Double.NaN;
        if (charges.isPresent()) {
            double maximum = charges.get().maximum().evaluate(context);
            double available = AbilityStorage.get(abilities, HolderHelper.id(ability), ChargesDataStorage.class).flatMap(ChargesDataStorage::remaining).orElse(maximum);
            if (!Double.isFinite(maximum) || maximum < 1.0D || !Double.isFinite(available) || available < 1.0D) {
                return PrepareResult.rejected(Failure.NO_CHARGES, null);
            }
            chargeBefore = available;
        }
        // One plan for the whole array: it evaluates every entry, checks the channels this payer can offer, and
        // writes nothing. The same plan is what the commit spends, so "can this be paid" has one implementation.
        CostTransaction.Planning costPlan = CostTransaction.plan(definition.costs(),
                CostContext.of(payer, context, CostOrigin.ABILITY), resources, itemDraft);
        if (!costPlan.ok()) return PrepareResult.rejected(costFailure(costPlan.failure()), null);
        return PrepareResult.prepared(new PreparedUse(ability, costPlan, Math.round(castTime), Math.round(cooldown), channelInterval, charges.isPresent(), chargeBefore));
    }

    private static CommitResult commit(PreparedUse use, AbilityAttachment abilities, ResourceHolderAttachment resources, long gameTime, LivingEntity payer) {
        if (abilities.isOnCooldown(HolderHelper.id(use.ability()), gameTime)) return CommitResult.rejected(Failure.COOLDOWN, null);
        CostTransaction.PayResult payment = CostTransaction.commit(use.costPlan(),
                CostContext.of(payer, CostOrigin.ABILITY), resources);
        if (!payment.paid()) return CommitResult.rejected(costFailure(payment.failure()), payment.failedResource());
        applyAbilityState(use, abilities, gameTime);
        return CommitResult.committed(payment.resources());
    }

    // Every cost failure ends the same way for the caller: a resource that ran out is named, anything else is
    // "this ability cannot be paid for".
    private static Failure costFailure(CostFailure failure) {
        return failure == CostFailure.INSUFFICIENT_RESOURCE ? Failure.INSUFFICIENT_RESOURCE : Failure.INSUFFICIENT_COST;
    }

    public static UseResult use(Holder<Ability> ability, Entity actor,
                                AbilityAttachment abilities, ResourceHolderAttachment resources, long gameTime,
                                FormulaContext context) {
        return use(ability, actor, abilities, resources, gameTime, context, true, null);
    }

    // For an ability the actor does not hold but an item of theirs carries: the filled item is its own
    // permission, and everything else is the ordinary path. origin is where it happens when that is not where
    // the actor is (a talisman on a display stand fires from the stand); null means the actor's own position.
    public static UseResult useCarried(Holder<Ability> ability, Entity actor,
                                       AbilityAttachment abilities, ResourceHolderAttachment resources, long gameTime,
                                       FormulaContext context, @Nullable Vec3 origin) {
        return use(ability, actor, abilities, resources, gameTime, context, false, origin);
    }

    private static UseResult use(Holder<Ability> ability, Entity actor,
                                 AbilityAttachment abilities, ResourceHolderAttachment resources, long gameTime,
                                 FormulaContext context, boolean requiresGrant, @Nullable Vec3 origin) {
        Ability definition = ability.value();
        if (actor instanceof LivingEntity living) {
            context = FormulaContexts.forEntity(living, context);
            context = withAbilityScaling(living, ability, context);
            if (!definition.elementAffinity().isEmpty() && context.value(DamageCalculationService.ELEMENT_MODIFIER) <= 0.0D)
                return UseResult.rejected(Failure.ELEMENT_AFFINITY, null);
        }
        // A carried ability must take effect at once: a cast is finished by walking the abilities the actor
        // *holds*, and a channel re-checks that grant on every pulse, so an item-granted one would never finish
        // its cast or would stop on its first tick. Refused here, before anything has been paid for.
        if (!requiresGrant && (definition.castTime().evaluate(context) > 0.0D
                || definition.type() instanceof ChannelledAbilityType))
            return UseResult.rejected(Failure.CARRIED_NOT_INSTANT, null);
        if (NeoForge.EVENT_BUS.post(new AbilityUseEvent.Pre(actor, ability, context)).isCanceled()) {
            return UseResult.rejected(Failure.CANCELLED, null);
        }
        if (!definition.condition().test(actor, context)) {
            return UseResult.rejected(Failure.CONDITION_FAILED, null);
        }
        if (!validateWord(definition, actor, context)) return UseResult.rejected(Failure.PERMISSION_DENIED, null);
        if (definition.type() instanceof CompositeAbilityType) {
            return useComposite(ability, actor, abilities, resources, gameTime, context, requiresGrant, origin);
        }
        PrepareResult prepared = prepare(ability, abilities, resources, gameTime, context,
                actor instanceof LivingEntity living ? living : null, requiresGrant, null);
        if (!prepared.approved()) return UseResult.rejected(prepared.failure(), prepared.failedResource());
        if (prepared.use().castTimeTicks() > 0L) {
            AbilityStorage.set(abilities, HolderHelper.id(ability), new CastDeadline(Math.addExact(gameTime, prepared.use().castTimeTicks())), gameTime);
            return UseResult.castingResult();
        }
        return finishPreparedUse(prepared.use(), definition, actor, abilities, resources, gameTime, context, origin);
    }

    public static UseResult finishCast(Holder<Ability> ability, Entity actor,
                                       AbilityAttachment abilities, ResourceHolderAttachment resources, long gameTime,
                                       FormulaContext context) {
        Ability definition = ability.value();
        if (actor instanceof LivingEntity living) {
            context = FormulaContexts.forEntity(living, context);
            context = withAbilityScaling(living, ability, context);
            if (!definition.elementAffinity().isEmpty() && context.value(DamageCalculationService.ELEMENT_MODIFIER) <= 0.0D)
                return UseResult.rejected(Failure.ELEMENT_AFFINITY, null);
        }
        if (!AbilityStorage.castDue(abilities, HolderHelper.id(ability), gameTime)) {
            return UseResult.castingResult();
        }
        AbilityStorage.clearCast(abilities, HolderHelper.id(ability), gameTime);
        if (!definition.condition().test(actor, context)) return UseResult.rejected(Failure.CONDITION_FAILED, null);
        if (!validateWord(definition, actor, context)) return UseResult.rejected(Failure.PERMISSION_DENIED, null);
        // Already started by something that could start one, so the grant it was approved under is not asked for
        // a second time.
        PrepareResult prepared = prepare(ability, abilities, resources, gameTime, context,
                actor instanceof LivingEntity living ? living : null, false, null);
        if (!prepared.approved()) return UseResult.rejected(prepared.failure(), prepared.failedResource());
        return finishPreparedUse(prepared.use(), definition, actor, abilities, resources, gameTime, context, null);
    }

    private static UseResult finishPreparedUse(PreparedUse preparedUse, Ability definition, Entity actor,
                                               AbilityAttachment abilities, ResourceHolderAttachment resources, long gameTime,
                                               FormulaContext context, @Nullable Vec3 origin) {
        Pre resourceEvent = new Pre(resources, preparedUse.costPlan().resources());
        if (NeoForge.EVENT_BUS.post(resourceEvent).isCanceled()) return UseResult.rejected(Failure.CANCELLED, null);
        // The event owns the price from here on: the plan being paid is the one it handed back.
        preparedUse.costPlan().resources().clear();
        preparedUse.costPlan().resources().putAll(resourceEvent.amounts());
        CommitResult committed = commit(preparedUse, abilities, resources, gameTime,
                actor instanceof LivingEntity living ? living : null);
        if (!committed.committed()) return UseResult.rejected(committed.failure(), committed.failedResource());
        if (definition.type() instanceof ChannelledAbilityType) {
            abilities.setChannelledAbility(HolderHelper.id(preparedUse.ability()));
            AbilityStorage.set(abilities, HolderHelper.id(preparedUse.ability()),
                    new ChannelPulse(Math.addExact(gameTime, preparedUse.channelIntervalTicks())), gameTime);
        }
        // A channel owns the ability until released, so it never runs the one-shot entity action; its
        // target action still fires on activation and then once per upkeep pulse.
        executeEffects(definition, actor, context, origin);
        NeoForge.EVENT_BUS.post(new Post(resources, committed.amounts()));
        NeoForge.EVENT_BUS.post(new AbilityUseEvent.Post(actor, preparedUse.ability(), context, committed.amounts()));
        if (actor instanceof ServerPlayer player)
            MxtCriteriaTriggers.ABILITY.get().trigger(player, HolderHelper.id(preparedUse.ability()));
        return UseResult.committed(committed.amounts());
    }

    // What one press costs when the press is not a cast: the condition, the cooldown and the entry's own costs,
    // paid once, in the same transaction a cast uses. A type that acts on a press calls this first unless it has
    // a reason of its own not to (see Toggable#gated).
    public static GateResult gate(ToggleContext context) {
        Holder<Ability> ability = context.ability();
        LivingEntity holder = context.holder();
        AbilityAttachment abilities = holder.getData(MxtAttachments.ABILITY_HOLDER);
        ResourceHolderAttachment resources = holder.getData(MxtAttachments.RESOURCE_HOLDER);
        long gameTime = holder.level().getGameTime();
        FormulaContext formula = context.formula();
        Ability definition = ability.value();
        if (!abilities.has(HolderHelper.id(ability))) return GateResult.rejected(Failure.NOT_GRANTED, null);
        if (abilities.isOnCooldown(HolderHelper.id(ability), gameTime)) return GateResult.rejected(Failure.COOLDOWN, null);
        if (!definition.condition().test(holder, formula)) return GateResult.rejected(Failure.CONDITION_FAILED, null);
        double cooldown = cooldownOf(ability, abilities, formula);
        if (!Double.isFinite(cooldown) || cooldown < 0.0D) return GateResult.rejected(Failure.INVALID_FORMULA, null);
        Player player = holder instanceof Player value ? value : null;
        CostTransaction.Planning plan = CostTransaction.plan(definition.costs(),
                CostContext.of(holder, formula, CostOrigin.ABILITY), resources, player == null ? null : new ItemCostDraft(player));
        if (!plan.ok()) return GateResult.rejected(costFailure(plan.failure()), null);
        CostTransaction.PayResult payment = CostTransaction.commit(plan, CostContext.of(holder, CostOrigin.ABILITY), resources);
        if (!payment.paid()) return GateResult.rejected(costFailure(payment.failure()), payment.failedResource());
        if (cooldown > 0.0D) {
            abilities.setCooldownUntil(HolderHelper.id(ability), Math.addExact(gameTime, Math.round(cooldown)));
            AbilityStorage.set(abilities, HolderHelper.id(ability), AbilityStorage.cooldown(abilities, HolderHelper.id(ability), cooldown), gameTime);
        }
        return GateResult.ok();
    }

    // A declared mxt:cooldown overrides the field, which is how a pack states a length once; the stored value is
    // written by every payment either way, so a condition can ask about a cooldown the pack never declared.
    private static double cooldownOf(Holder<Ability> ability, AbilityAttachment abilities, FormulaContext context) {
        return kind(ability.value(), CooldownDataStorage.class)
                .map(storage -> storage.ticks().evaluate(context))
                .orElseGet(() -> ability.value().cooldown().evaluate(context));
    }

    // Server entity tick bridge only.
    public static ChannelResult tickChannel(Holder<Ability> ability, Entity actor,
                                            AbilityAttachment abilities, ResourceHolderAttachment resources, long gameTime,
                                            FormulaContext context) {
        Ability definition = ability.value();
        if (actor instanceof LivingEntity living) {
            context = withAbilityScaling(living, ability, FormulaContexts.forEntity(living, context));
            if (!definition.elementAffinity().isEmpty() && context.value(DamageCalculationService.ELEMENT_MODIFIER) <= 0.0D) {
                stopChannel(abilities);
                return ChannelResult.stopped(Failure.ELEMENT_AFFINITY);
            }
        }
        if (abilities.channelledAbility().filter(HolderHelper.id(ability)::equals).isEmpty()) return ChannelResult.inactive();
        if (!abilities.has(HolderHelper.id(ability)) || !(definition.type() instanceof ChannelledAbilityType(
                NumberProvider tickInterval,
                List<Cost> upkeepCosts
        ))) {
            stopChannel(abilities);
            return ChannelResult.stopped(Failure.NOT_GRANTED);
        }
        long nextTick = Math.round(AbilityStorage.get(abilities, HolderHelper.id(ability), ChannelPulse.class).map(ChannelPulse::nextTick).orElse((double) gameTime));
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
        CostContext upkeepContext = CostContext.of(actor instanceof LivingEntity living ? living : null, context, CostOrigin.CHANNEL_UPKEEP);
        CostTransaction.Planning upkeep = CostTransaction.plan(upkeepCosts, upkeepContext);
        if (!upkeep.ok()) {
            stopChannel(abilities);
            return ChannelResult.stopped(costFailure(upkeep.failure()));
        }
        Pre resourceEvent = new Pre(resources, upkeep.resources());
        if (NeoForge.EVENT_BUS.post(resourceEvent).isCanceled()) {
            stopChannel(abilities);
            return ChannelResult.stopped(Failure.CANCELLED);
        }
        upkeep.resources().clear();
        upkeep.resources().putAll(resourceEvent.amounts());
        CostTransaction.PayResult payment = CostTransaction.commit(upkeep, upkeepContext);
        if (!payment.paid()) {
            stopChannel(abilities);
            return ChannelResult.stopped(costFailure(payment.failure()));
        }
        // A channel is never carried by an item (see useCarried), so its pulses happen where the actor is.
        executeEffects(definition, actor, context, null);
        NeoForge.EVENT_BUS.post(new Post(resources, payment.resources()));
        long intervalTicks = Math.max(1L, Math.round(interval));
        long followingTick = Math.addExact(gameTime, intervalTicks);
        AbilityStorage.set(abilities, HolderHelper.id(ability), new ChannelPulse(followingTick), gameTime);
        return ChannelResult.pulsed(followingTick, payment.resources());
    }

    public static boolean stopChannel(AbilityAttachment abilities) {
        if (abilities.channelledAbility().isEmpty()) return false;
        abilities.setChannelledAbility(null);
        return true;
    }

    // Clears a pending cast without touching resources, cooldowns or unrelated stored state.
    public static boolean cancelCast(Holder<Ability> ability, AbilityAttachment abilities, long gameTime) {
        if (!AbilityStorage.hasCast(abilities, HolderHelper.id(ability))) return false;
        AbilityStorage.clearCast(abilities, HolderHelper.id(ability), gameTime);
        return true;
    }

    private static boolean validateWord(Ability definition, Entity actor, FormulaContext context) {
        if (!(definition.type() instanceof WordAbilityType(
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

    // The one-shot entity action runs for every activation, WordAbilityType excepted because its payload replaces
    // the whole pipeline. origin is where the payload happens when the activation has a place of its own; null
    // means where the actor is, and a nested action inherits it through the context.
    private static void executeEffects(Ability definition, Entity actor, FormulaContext context, @Nullable Vec3 origin) {
        try {
            if (definition.type() instanceof WordAbilityType word) {
                executeWord(word, actor, context);
                return;
            }
            definition.entityAction().execute(new EntityActionContext(actor, context, origin));
        } catch (RuntimeException exception) {
            MiXianTu.LOGGER.error("Ability entity action failed", exception);
        }
        executeTargetAction(definition, actor, context, origin);
    }

    private static void executeWord(WordAbilityType word, Entity actor, FormulaContext context) {
        if (word.effect() == WordEffect.SELF_HEAL && actor instanceof LivingEntity living) {
            living.heal((float) word.amount().evaluate(context));
        } else if (word.effect() == WordEffect.PURGE_SELF_CURSES) {
            CurseHolderAttachment holder = actor.getData(MxtAttachments.CURSE_HOLDER);
            new LinkedList<>(holder.instances().keySet()).forEach(curse ->
                    CurseService.remove(actor, curse, Reason.EXPLICIT, -1L, context));
        }
    }

    // Every required child is validated against detached drafts and all costs are committed before any action
    // runs, because world actions are deliberately never rolled back.
    private static UseResult useComposite(Holder<Ability> composite, Entity actor,
                                          AbilityAttachment abilities, ResourceHolderAttachment resources, long gameTime,
                                          FormulaContext context, boolean requiresGrant, @Nullable Vec3 origin) {
        if (!(composite.value().type() instanceof CompositeAbilityType(
                List<Holder<Ability>> abilities1, boolean allRequired
        )))
            return UseResult.rejected(Failure.INVALID_FORMULA, null);
        if (!allRequired) {
            if (abilities1.isEmpty()) return UseResult.rejected(Failure.NOT_GRANTED, null);
            return use(abilities1.getFirst(), actor, abilities, resources, gameTime, context, requiresGrant, origin);
        }

        LivingEntity payer = actor instanceof LivingEntity value ? value : null;
        Player player = payer instanceof Player value ? value : null;
        AbilityAttachment abilityDraft = abilities.copy();
        ResourceHolderAttachment resourceDraft = resources.copy();
        ItemCostDraft itemDraft = player == null ? null : new ItemCostDraft(player);
        List<CompositeStep> steps = new LinkedList<>();
        LinkedHashMap<Identifier, Double> paid = new LinkedHashMap<>();
        for (Holder<Ability> childHolder : abilities1) {
            Ability child = childHolder.value();
            FormulaContext childContext = context;
            if (actor instanceof LivingEntity living) {
                childContext = withAbilityScaling(living, childHolder, FormulaContexts.forEntity(living, context));
                if (!child.elementAffinity().isEmpty() && childContext.value(DamageCalculationService.ELEMENT_MODIFIER) <= 0.0D)
                    return UseResult.rejected(Failure.ELEMENT_AFFINITY, null);
            }
            if (NeoForge.EVENT_BUS.post(new AbilityUseEvent.Pre(actor, childHolder, childContext)).isCanceled())
                return UseResult.rejected(Failure.CANCELLED, null);
            if (!child.condition().test(actor, childContext)) return UseResult.rejected(Failure.CONDITION_FAILED, null);
            if (!validateWord(child, actor, childContext)) return UseResult.rejected(Failure.PERMISSION_DENIED, null);
            PrepareResult prepared = prepare(childHolder, abilityDraft, resourceDraft, gameTime, childContext,
                    actor instanceof LivingEntity living ? living : null, requiresGrant, itemDraft);
            if (!prepared.approved()) return UseResult.rejected(prepared.failure(), prepared.failedResource());
            if (prepared.use().castTimeTicks() > 0L)
                return UseResult.rejected(Failure.INVALID_FORMULA, null);
            Pre resourceEvent = new Pre(resources, prepared.use().costPlan().resources());
            if (NeoForge.EVENT_BUS.post(resourceEvent).isCanceled()) return UseResult.rejected(Failure.CANCELLED, null);
            prepared.use().costPlan().resources().clear();
            prepared.use().costPlan().resources().putAll(resourceEvent.amounts());
            // The draft is what the next child is checked against; the real payment happens below, in order.
            Result preview = ResourceTransactions.tryConsume(payer, resourceDraft,
                    new Evaluation(prepared.use().costPlan().resources()));
            if (!preview.committed())
                return UseResult.rejected(Failure.INSUFFICIENT_RESOURCE, preview.failedResource());
            applyAbilityState(prepared.use(), abilityDraft, gameTime);
            prepared.use().costPlan().resources().forEach((id, amount) -> paid.merge(id, amount, Double::sum));
            steps.add(new CompositeStep(childHolder, prepared.use(), childContext));
        }
        for (CompositeStep step : steps) {
            CommitResult committed = commit(step.use(), abilities, resources, gameTime, payer);
            if (!committed.committed()) {
                MiXianTu.LOGGER.error("Composite ability {} failed after prevalidation: {}", HolderHelper.id(composite), committed.failure());
                return UseResult.rejected(committed.failure(), committed.failedResource());
            }
        }
        for (CompositeStep step : steps) {
            if (step.ability().value().type() instanceof ChannelledAbilityType) {
                abilities.setChannelledAbility(HolderHelper.id(step.use().ability()));
                AbilityStorage.set(abilities, HolderHelper.id(step.use().ability()),
                        new ChannelPulse(Math.addExact(gameTime, step.use().channelIntervalTicks())), gameTime);
            } else {
                executeEffects(step.ability().value(), actor, step.context(), origin);
            }
            NeoForge.EVENT_BUS.post(new Post(resources, step.use().costPlan().resources()));
            NeoForge.EVENT_BUS.post(new AbilityUseEvent.Post(actor, step.ability(), step.context(), step.use().costPlan().resources()));
            if (actor instanceof ServerPlayer serverPlayer)
                MxtCriteriaTriggers.ABILITY.get().trigger(serverPlayer, HolderHelper.id(step.ability()));
        }
        return UseResult.committed(paid);
    }

    private static void applyAbilityState(PreparedUse use, AbilityAttachment abilities, long gameTime) {
        abilities.setCooldownUntil(HolderHelper.id(use.ability()), Math.addExact(gameTime, use.cooldownTicks()));
        AbilityStorage.set(abilities, HolderHelper.id(use.ability()), AbilityStorage.cooldown(abilities, HolderHelper.id(use.ability()), use.cooldownTicks()), gameTime);
        if (use.consumeCharge())
            AbilityStorage.set(abilities, HolderHelper.id(use.ability()), AbilityStorage.charges(abilities, HolderHelper.id(use.ability()), Math.max(0.0D, use.chargeBefore() - 1.0D)), gameTime);
    }

    // Put on the context here, where the ability being cast is still known, because a damage action only ever
    // sees a formula context: the damage pipeline reads both names on the attacker's side of a hit.
    private static FormulaContext withAbilityScaling(LivingEntity actor, Holder<Ability> ability, FormulaContext context) {
        Ability definition = ability.value();
        FormulaContext scaled = context;
        if (!definition.elementAffinity().isEmpty()) {
            double modifier = CultivationAffinity.abilityMultiplier(actor.getData(MxtAttachments.SPIRIT_IDENTITY),
                    definition.elementAffinity(), context, definition.elementAffinityMode());
            scaled = scaled.with(DamageCalculationService.ELEMENT_MODIFIER, modifier);
        }
        return scaled.with(DamageCalculationService.DAMAGE_MULTIPLIER, SkillStageService.damageMultiplier(actor, HolderHelper.id(ability)));
    }

    private static <T extends DataStorage> Optional<T> kind(Ability definition, Class<T> type) {
        return definition.storages().stream().filter(type::isInstance).map(type::cast).findFirst();
    }

    // One failing action never stops the rest.
    private static void executeTargetAction(Ability definition, Entity actor, FormulaContext context, @Nullable Vec3 origin) {
        try {
            definition.targetSelector().select(actor, context, origin)
                    .forEach(target -> executeTargetAction(definition, actor, target, context, origin));
        } catch (RuntimeException exception) {
            MiXianTu.LOGGER.error("Ability target selection failed", exception);
        }
    }

    public static void executeTargetAction(Ability definition, Entity actor, Entity target, FormulaContext context) {
        executeTargetAction(definition, actor, target, context, null);
    }

    private static void executeTargetAction(Ability definition, Entity actor, Entity target, FormulaContext context,
                                            @Nullable Vec3 origin) {
        try {
            FormulaContext targetContext = actor instanceof LivingEntity caster && target instanceof LivingEntity livingTarget
                    ? FormulaContexts.forEntities(caster, livingTarget, context) : context;
            if (definition.targetCondition().test(actor, target, targetContext))
                // The place travels with the activation: a bi-entity action that moves an endpoint to "the actor"
                // moves it to where the ability happened - a stand's ward pulls to the stand.
                definition.biEntityAction().execute(actor, target, new BiEntityActionContext(actor, target, targetContext, origin));
        } catch (RuntimeException exception) {
            MiXianTu.LOGGER.error("Ability target action failed", exception);
        }
    }

    private record CompositeStep(Holder<Ability> ability, PreparedUse use, FormulaContext context) {
    }

    public enum Failure {DISABLED, NOT_GRANTED, COOLDOWN, INSUFFICIENT_RESOURCE, INSUFFICIENT_COST, INVALID_FORMULA, CONDITION_FAILED, NO_CHARGES, CANCELLED, PERMISSION_DENIED, ELEMENT_AFFINITY, SERVER_ONLY, CARRIED_NOT_INSTANT}

    public record PreparedUse(Holder<Ability> ability, CostTransaction.Planning costPlan, long castTimeTicks,
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

    public record GateResult(boolean approved, Failure failure, Identifier failedResource) {
        private static GateResult ok() {
            return new GateResult(true, null, null);
        }

        private static GateResult rejected(Failure failure, Identifier resource) {
            return new GateResult(false, failure, resource);
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
