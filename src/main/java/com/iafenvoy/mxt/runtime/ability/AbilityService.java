package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.ability.*;
import com.iafenvoy.mxt.data.ability.type.CompositeAbilityType;
import com.iafenvoy.mxt.data.ability.type.WordAbilityType;
import com.iafenvoy.mxt.data.ability.type.WordAbilityType.WordEffect;
import com.iafenvoy.mxt.data.cost.CostPayment;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostFailure;
import com.iafenvoy.mxt.data.cost.context.CostOrigin;
import com.iafenvoy.mxt.data.storage.builtin.ChargesDataStorage;
import com.iafenvoy.mxt.data.storage.runtime.CastDeadline;
import com.iafenvoy.mxt.data.storage.runtime.ChannelPulse;
import com.iafenvoy.mxt.event.AbilityUseEvent;
import com.iafenvoy.mxt.event.ResourceConsumeEvent.Post;
import com.iafenvoy.mxt.event.ResourceConsumeEvent.Pre;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtCriteriaTriggers;
import com.iafenvoy.mxt.runtime.cultivation.CultivationAffinity;
import com.iafenvoy.mxt.runtime.damage.DamageCalculationService;
import com.iafenvoy.mxt.runtime.progression.ProgressionDamageMultiplier;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
 * script or a carried artifact - it takes the same path, and the holder's id is what the grant ledger and the
 * stored state are keyed by.
 */
public final class AbilityService {
    private AbilityService() {
    }

    private static PrepareResult prepare(Holder<Ability> ability, AbilityAttachment abilities, long gameTime,
                                         FormulaContext context, LivingEntity payer, boolean requiresGrant,
                                         @Nullable CostPayment payment) {
        Ability definition = ability.value();
        if (requiresGrant && !abilities.has(HolderHelper.id(ability)))
            return PrepareResult.rejected(Failure.NOT_GRANTED, null);
        if (AbilityStorage.onCooldown(abilities, HolderHelper.id(ability), gameTime))
            return PrepareResult.rejected(Failure.COOLDOWN, null);
        double castTime = definition.castTime().evaluate(context);
        double cooldown = cooldownOf(ability, context);
        if (!Double.isFinite(castTime) || castTime < 0.0D || !Double.isFinite(cooldown) || cooldown < 0.0D) {
            return PrepareResult.rejected(Failure.INVALID_FORMULA, null);
        }
        long channelInterval = 0L;
        if (definition.type() instanceof ChannelSource channel) {
            double interval = channel.channelInterval().evaluate(context);
            if (!Double.isFinite(interval) || interval <= 0.0D || interval > Long.MAX_VALUE) {
                return PrepareResult.rejected(Failure.INVALID_FORMULA, null);
            }
            channelInterval = Math.max(1L, Math.round(interval));
        }
        Optional<ChargesDataStorage> charges = definition.charges().map(ChargesDataStorage.Settings::declared);
        double chargeBefore = Double.NaN;
        if (charges.isPresent()) {
            double maximum = charges.get().maximum().evaluate(context);
            double available = AbilityStorage.get(abilities, HolderHelper.id(ability), ChargesDataStorage.class).flatMap(ChargesDataStorage::remaining).orElse(maximum);
            if (!Double.isFinite(maximum) || maximum < 1.0D || !Double.isFinite(available) || available < 1.0D) {
                return PrepareResult.rejected(Failure.NO_CHARGES, null);
            }
            chargeBefore = available;
        }
        // One payment for the whole array: loading it evaluates every entry and merges the entries of a type into
        // that type's draft, testing it answers whether the payer can afford all of it, and nothing is written. The
        // same payment is what the commit spends, so "can this be paid" has one implementation.
        CostPayment cost = payment != null ? payment
                : CostPayment.of(CostContext.of(payer, context, CostOrigin.ABILITY));
        Optional<CostFailure> refusal = cost.loadAndTest(definition.costs());
        if (refusal.isPresent()) return PrepareResult.rejected(costFailure(refusal.get()), cost.failedResource());
        return PrepareResult.prepared(new PreparedUse(ability, cost, Math.round(castTime), Math.round(cooldown), channelInterval, charges.isPresent(), chargeBefore));
    }

    private static CommitResult commit(PreparedUse use, AbilityAttachment abilities, long gameTime) {
        if (AbilityStorage.onCooldown(abilities, HolderHelper.id(use.ability()), gameTime))
            return CommitResult.rejected(Failure.COOLDOWN, null);
        CostPayment.Result payment = use.payment().commit();
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

    /**
     * Merges one carried ability's costs into a payment without executing anything, so a caller paying for several
     * things in one act can check the whole payment before any of them happens. Null means the payment went through;
     * anything else is why this ability would not fire. It reads the same refusals as {@link #useCarried} except the
     * ones only a run can answer (a reach that lands on nobody).
     */
    public static @Nullable Failure reserveCost(Holder<Ability> ability, Entity actor, AbilityAttachment abilities,
                                                CostPayment payment, long gameTime, FormulaContext context) {
        Ability definition = ability.value();
        if (definition.castTime().evaluate(context) > 0.0D || definition.type() instanceof ChannelSource)
            return Failure.CARRIED_NOT_INSTANT;
        if (!definition.condition().test(actor, context)) return Failure.CONDITION_FAILED;
        PrepareResult prepared = prepare(ability, abilities, gameTime, context,
                actor instanceof LivingEntity living ? living : null, false, payment);
        return prepared.approved() ? null : prepared.failure();
    }

    private static UseResult use(Holder<Ability> ability, Entity actor,
                                 AbilityAttachment abilities, ResourceHolderAttachment resources, long gameTime,
                                 FormulaContext context, boolean requiresGrant, @Nullable Vec3 origin) {
        // Every use is authoritative: paying, writing state and running actions belong to the server, and a client
        // that asked anyway gets the same answer the script boundary gives.
        if (actor.level().isClientSide()) return UseResult.rejected(Failure.SERVER_ONLY, null);
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
                || definition.type() instanceof ChannelSource))
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
        PrepareResult prepared = prepare(ability, abilities, gameTime, context,
                actor instanceof LivingEntity living ? living : null, requiresGrant, null);
        if (!prepared.approved()) return UseResult.rejected(prepared.failure(), prepared.failedResource());
        if (prepared.use().castTimeTicks() > 0L) {
            AbilityStorage.value(abilities, HolderHelper.id(ability), CastDeadline.class, new CastDeadline(CastDeadline.NO_CAST), gameTime)
                    .start(Math.addExact(gameTime, prepared.use().castTimeTicks()));
            return UseResult.castingResult();
        }
        return finishPreparedUse(prepared.use(), definition, actor, abilities, resources, gameTime, context, origin);
    }

    public static UseResult finishCast(Holder<Ability> ability, Entity actor,
                                       AbilityAttachment abilities, ResourceHolderAttachment resources, long gameTime,
                                       FormulaContext context) {
        if (actor.level().isClientSide()) return UseResult.rejected(Failure.SERVER_ONLY, null);
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
        PrepareResult prepared = prepare(ability, abilities, gameTime, context,
                actor instanceof LivingEntity living ? living : null, false, null);
        if (!prepared.approved()) return UseResult.rejected(prepared.failure(), prepared.failedResource());
        return finishPreparedUse(prepared.use(), definition, actor, abilities, resources, gameTime, context, null);
    }

    private static UseResult finishPreparedUse(PreparedUse preparedUse, Ability definition, Entity actor,
                                               AbilityAttachment abilities, ResourceHolderAttachment resources, long gameTime,
                                               FormulaContext context, @Nullable Vec3 origin) {
        // Decided before the press pays for anything: a reach that lands on nobody is a refusal, not a paid silence.
        Failure unreached = applierFailure(definition, actor, context, origin);
        if (unreached != null) return UseResult.rejected(unreached, null);
        Pre resourceEvent = new Pre(resources, preparedUse.payment().resources());
        if (NeoForge.EVENT_BUS.post(resourceEvent).isCanceled()) return UseResult.rejected(Failure.CANCELLED, null);
        // The event owns the amounts from here on: the payment being made is the one it handed back.
        preparedUse.payment().resources().clear();
        preparedUse.payment().resources().putAll(resourceEvent.amounts());
        CommitResult committed = commit(preparedUse, abilities, gameTime);
        if (!committed.committed()) return UseResult.rejected(committed.failure(), committed.failedResource());
        if (definition.type() instanceof ChannelSource) {
            abilities.setChannelledAbility(preparedUse.ability());
            AbilityStorage.value(abilities, HolderHelper.id(preparedUse.ability()), ChannelPulse.class, new ChannelPulse(0.0D), gameTime)
                    .set(Math.addExact(gameTime, preparedUse.channelIntervalTicks()));
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
        if (holder.level().isClientSide()) return GateResult.rejected(Failure.SERVER_ONLY, null);
        AbilityAttachment abilities = holder.getData(MxtAttachments.ABILITY_HOLDER);
        long gameTime = holder.level().getGameTime();
        FormulaContext formula = context.formula();
        Ability definition = ability.value();
        if (!abilities.has(HolderHelper.id(ability))) return GateResult.rejected(Failure.NOT_GRANTED, null);
        if (AbilityStorage.onCooldown(abilities, HolderHelper.id(ability), gameTime))
            return GateResult.rejected(Failure.COOLDOWN, null);
        if (!definition.condition().test(holder, formula)) return GateResult.rejected(Failure.CONDITION_FAILED, null);
        double cooldown = cooldownOf(ability, formula);
        if (!Double.isFinite(cooldown) || cooldown < 0.0D) return GateResult.rejected(Failure.INVALID_FORMULA, null);
        CostPayment plan = CostPayment.of(CostContext.of(holder, formula, CostOrigin.ABILITY));
        Optional<CostFailure> refusal = plan.loadAndTest(definition.costs());
        if (refusal.isPresent()) return GateResult.rejected(costFailure(refusal.get()), null);
        CostPayment.Result payment = plan.commit();
        if (!payment.paid()) return GateResult.rejected(costFailure(payment.failure()), payment.failedResource());
        if (cooldown > 0.0D)
            AbilityStorage.startCooldown(abilities, HolderHelper.id(ability), cooldown, gameTime);
        return GateResult.ok();
    }

    // The ability's own field is the one length there is: the state written on every payment carries the length it
    // was actually paid for, and the field is what a length is read from before anything has been paid.
    private static double cooldownOf(Holder<Ability> ability, FormulaContext context) {
        return ability.value().type() instanceof CooldownSource source ? source.cooldown().evaluate(context) : 0.0D;
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
        if (abilities.channelledAbility().filter(channelled -> channelled.is(HolderHelper.id(ability))).isEmpty())
            return ChannelResult.inactive();
        if (!abilities.has(HolderHelper.id(ability)) || !(definition.type() instanceof ChannelSource channel)) {
            stopChannel(abilities);
            return ChannelResult.stopped(Failure.NOT_GRANTED);
        }
        long nextTick = Math.round(AbilityStorage.get(abilities, HolderHelper.id(ability), ChannelPulse.class)
                .map(ChannelPulse::nextTick).orElse((double) gameTime));
        if (gameTime < nextTick) return ChannelResult.waiting(nextTick);
        if (!definition.condition().test(actor, context)) {
            stopChannel(abilities);
            return ChannelResult.stopped(Failure.CONDITION_FAILED);
        }
        double interval = channel.channelInterval().evaluate(context);
        if (!Double.isFinite(interval) || interval <= 0.0D || interval > Long.MAX_VALUE) {
            stopChannel(abilities);
            return ChannelResult.stopped(Failure.INVALID_FORMULA);
        }
        CostContext upkeepContext = CostContext.of(actor instanceof LivingEntity living ? living : null, context, CostOrigin.CHANNEL_UPKEEP);
        CostPayment upkeep = CostPayment.of(upkeepContext);
        Optional<CostFailure> unpaid = upkeep.loadAndTest(channel.upkeepCosts());
        if (unpaid.isPresent()) {
            stopChannel(abilities);
            return ChannelResult.stopped(costFailure(unpaid.get()));
        }
        Pre resourceEvent = new Pre(resources, upkeep.resources());
        if (NeoForge.EVENT_BUS.post(resourceEvent).isCanceled()) {
            stopChannel(abilities);
            return ChannelResult.stopped(Failure.CANCELLED);
        }
        upkeep.resources().clear();
        upkeep.resources().putAll(resourceEvent.amounts());
        CostPayment.Result payment = upkeep.commit();
        if (!payment.paid()) {
            stopChannel(abilities);
            return ChannelResult.stopped(costFailure(payment.failure()));
        }
        // A channel is never carried by an item (see useCarried), so its pulses happen where the actor is.
        executeEffects(definition, actor, context, null);
        NeoForge.EVENT_BUS.post(new Post(resources, payment.resources()));
        long intervalTicks = Math.max(1L, Math.round(interval));
        long followingTick = Math.addExact(gameTime, intervalTicks);
        AbilityStorage.value(abilities, HolderHelper.id(ability), ChannelPulse.class, new ChannelPulse(0.0D), gameTime).set(followingTick);
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
        if (!(definition.type() instanceof WordAbilityType word)) return true;
        if (word.requiresOperator() && (!(actor instanceof ServerPlayer player) || !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)))
            return false;
        if (word.effect() != WordEffect.SELF_HEAL) return true;
        try {
            double amount = word.amount().evaluate(context);
            return Double.isFinite(amount) && amount >= 0.0D && amount <= Float.MAX_VALUE;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    // Everything that takes effect goes through the type's own payload, so "what this ability does" is one call
    // and a type whose effect is not a payload answers it itself. origin is where the payload happens when the
    // activation has a place of its own; null means where the actor is, and a nested action inherits it.
    private static void executeEffects(Ability definition, Entity actor, FormulaContext context, @Nullable Vec3 origin) {
        AbilityEffect.run(definition.type(), actor, context, origin);
    }

    // What an applier activation would land on, asked before it is paid for. A payload with no one-target half would
    // do nothing at all, which is a different refusal from a reach that found nobody.
    private static @Nullable Failure applierFailure(Ability definition, Entity actor, FormulaContext context,
                                                    @Nullable Vec3 origin) {
        if (!(definition.type() instanceof AbilityApplier applier)) return null;
        if (!(applier.payload().value().type() instanceof ActionCarrier)) return Failure.NOT_APPLICABLE;
        try {
            return applier.reach(actor, context, origin).findAny().isPresent() ? null : Failure.NO_TARGET;
        } catch (RuntimeException exception) {
            // A selector or condition that cannot be evaluated is a broken definition, not an empty reach.
            MiXianTu.LOGGER.error("Ability target selection failed", exception);
            return Failure.INVALID_FORMULA;
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
        AbilityAttachment abilityDraft = abilities.copy();
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
            // Each child gets its own payment, in its own formula context, because the event below owns that child's
            // amounts and because a composite is one boundary rather than one payment.
            CostPayment payment = CostPayment.of(CostContext.of(payer, childContext, CostOrigin.ABILITY));
            PrepareResult prepared = prepare(childHolder, abilityDraft, gameTime, childContext,
                    payer, requiresGrant, payment);
            if (!prepared.approved()) return UseResult.rejected(prepared.failure(), prepared.failedResource());
            if (prepared.use().castTimeTicks() > 0L)
                return UseResult.rejected(Failure.INVALID_FORMULA, null);
            // A child that reaches nobody refuses the whole composite, before any child has been paid for.
            Failure unreached = applierFailure(child, actor, childContext, origin);
            if (unreached != null) return UseResult.rejected(unreached, null);
            Pre resourceEvent = new Pre(resources, prepared.use().payment().resources());
            if (NeoForge.EVENT_BUS.post(resourceEvent).isCanceled()) return UseResult.rejected(Failure.CANCELLED, null);
            prepared.use().payment().resources().clear();
            prepared.use().payment().resources().putAll(resourceEvent.amounts());
            applyAbilityState(prepared.use(), abilityDraft, gameTime);
            resourceEvent.amounts().forEach((id, amount) -> paid.merge(id, amount, Double::sum));
            steps.add(new CompositeStep(childHolder, prepared.use(), childContext, Map.copyOf(resourceEvent.amounts())));
        }
        // One boundary for every child: a child that refuses - whether its store is short or its items do not fit -
        // puts back what the earlier ones took, and no child's cooldown or charges is written before the whole group
        // is paid for.
        List<CostPayment> payments = new ArrayList<>();
        for (CompositeStep step : steps) {
            if (AbilityStorage.onCooldown(abilities, HolderHelper.id(step.use().ability()), gameTime))
                return UseResult.rejected(Failure.COOLDOWN, null);
            payments.add(step.use().payment());
        }
        CostPayment.Result payment = CostPayment.payAll(payments);
        if (!payment.paid()) return UseResult.rejected(costFailure(payment.failure()), payment.failedResource());
        for (CompositeStep step : steps) applyAbilityState(step.use(), abilities, gameTime);
        for (CompositeStep step : steps) {
            if (step.ability().value().type() instanceof ChannelSource) {
                abilities.setChannelledAbility(step.use().ability());
                AbilityStorage.value(abilities, HolderHelper.id(step.use().ability()), ChannelPulse.class, new ChannelPulse(0.0D), gameTime)
                        .set(Math.addExact(gameTime, step.use().channelIntervalTicks()));
            } else {
                executeEffects(step.ability().value(), actor, step.context(), origin);
            }
            NeoForge.EVENT_BUS.post(new Post(resources, step.amounts()));
            NeoForge.EVENT_BUS.post(new AbilityUseEvent.Post(actor, step.ability(), step.context(), step.amounts()));
            if (actor instanceof ServerPlayer serverPlayer)
                MxtCriteriaTriggers.ABILITY.get().trigger(serverPlayer, HolderHelper.id(step.ability()));
        }
        return UseResult.committed(paid);
    }

    private static void applyAbilityState(PreparedUse use, AbilityAttachment abilities, long gameTime) {
        Identifier id = HolderHelper.id(use.ability());
        AbilityStorage.startCooldown(abilities, id, use.cooldownTicks(), gameTime);
        if (use.consumeCharge()) {
            ChargesDataStorage declaration = use.ability().value().charges()
                    .map(ChargesDataStorage.Settings::declared).orElse(ChargesDataStorage.INSTANCE);
            AbilityStorage.charges(abilities, id, declaration, gameTime).setRemaining(Math.max(0.0D, use.chargeBefore() - 1.0D), gameTime);
        }
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
        return scaled.with(DamageCalculationService.DAMAGE_MULTIPLIER, ProgressionDamageMultiplier.of(actor, HolderHelper.id(ability)));
    }

    private record CompositeStep(Holder<Ability> ability, PreparedUse use, FormulaContext context,
                                 Map<Identifier, Double> amounts) {
    }

    public enum Failure {DISABLED, NOT_GRANTED, COOLDOWN, INSUFFICIENT_RESOURCE, INSUFFICIENT_COST, INVALID_FORMULA, CONDITION_FAILED, NO_CHARGES, CANCELLED, PERMISSION_DENIED, ELEMENT_AFFINITY, SERVER_ONLY, CARRIED_NOT_INSTANT, NO_TARGET, NOT_APPLICABLE}

    public record PreparedUse(Holder<Ability> ability, CostPayment payment, long castTimeTicks,
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
