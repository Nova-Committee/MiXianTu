package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.ability.*;
import com.iafenvoy.mxt.data.ability.type.WordAbilityType;
import com.iafenvoy.mxt.data.ability.type.WordAbilityType.WordEffect;
import com.iafenvoy.mxt.data.cost.CostPayment;
import com.iafenvoy.mxt.data.cost.context.CostContext;
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
import com.iafenvoy.mxt.runtime.resulting.Failure;
import com.iafenvoy.mxt.runtime.resulting.Failures;
import com.iafenvoy.mxt.runtime.resulting.Result;
import com.iafenvoy.mxt.runtime.resulting.ResultWithData;
import com.iafenvoy.mxt.runtime.resulting.failures.AbilityFailure;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import com.iafenvoy.mxt.util.profiling.LoggerDispatcher;
import com.iafenvoy.mxt.util.profiling.LoggingLevel;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Server-side ability cost and cooldown transaction: actions are committed only after this service approves
 * them, as one server-thread operation. World actions are deliberately never rolled back, which is why the price
 * is checked before any of them runs.
 *
 * <p>Everything here takes the ability as its registry holder: however it was granted - a book, a command, a
 * script or a carried artifact - it takes the same path, and the holder's id is what the grant ledger and the
 * stored state are keyed by.
 */
public final class AbilityService {
    private static final LoggerDispatcher LOGGER = LoggerDispatcher.of(LogUtils.getLogger(), LoggingLevel.ERROR, LoggingLevel.ERROR);

    private AbilityService() {
    }

    private static ResultWithData<PreparedUse> prepare(Holder<Ability> ability, AbilityAttachment abilities, long gameTime,
                                                       FormulaContext context, LivingEntity payer, boolean requiresGrant,
                                                       @Nullable CostPayment payment) {
        Ability definition = ability.value();
        if (requiresGrant && !abilities.has(ability))
            return ResultWithData.failure(null, AbilityFailure.NOT_GRANTED, null);
        if (AbilityStorage.onCooldown(abilities, ability, gameTime))
            return ResultWithData.failure(null, Failures.COOLDOWN, null);
        double castTime = definition.castTime().evaluate(context);
        double cooldown = cooldownOf(ability, context);
        if (!Double.isFinite(castTime) || castTime < 0.0D || !Double.isFinite(cooldown) || cooldown < 0.0D) {
            return ResultWithData.failure(null, Failures.INVALID_FORMULA, null);
        }
        long channelInterval = 0L;
        if (definition.type() instanceof ChannelSource channel) {
            double interval = channel.channelInterval().evaluate(context);
            if (!Double.isFinite(interval) || interval <= 0.0D || interval > Long.MAX_VALUE) {
                return ResultWithData.failure(null, Failures.INVALID_FORMULA, null);
            }
            channelInterval = Math.max(1L, Math.round(interval));
        }
        Optional<ChargesDataStorage> charges = definition.charges().map(ChargesDataStorage.Settings::declared);
        double chargeBefore = Double.NaN;
        if (charges.isPresent()) {
            double maximum = charges.get().maximum().evaluate(context);
            OptionalDouble stored = AbilityStorage.get(abilities, ability, ChargesDataStorage.class).map(ChargesDataStorage::remaining).orElseGet(OptionalDouble::empty);
            double available = stored.orElse(maximum);
            if (!Double.isFinite(maximum) || maximum < 1.0D || !Double.isFinite(available) || available < 1.0D) {
                return ResultWithData.failure(null, AbilityFailure.NO_CHARGES, null);
            }
            chargeBefore = available;
        }
        // One payment for the whole array: pricing it binds every entry to a fixed amount and writes nothing, and the
        // check right after asks every store whether the whole bill can be paid. The same payment is what the commit
        // spends, so "can this be paid" has one implementation.
        CostPayment cost = payment != null ? payment
                : CostPayment.of(CostContext.of(payer, context, CostOrigin.ABILITY));
        Optional<Failure> refusal = cost.price(definition.costs());
        if (refusal.isPresent()) return ResultWithData.failure(null, refusal.get(), cost.failedResource());
        refusal = cost.test();
        if (refusal.isPresent()) return ResultWithData.failure(null, refusal.get(), cost.failedResource());
        return ResultWithData.success(new PreparedUse(ability, cost, Math.round(castTime), Math.round(cooldown), channelInterval, charges.isPresent(), chargeBefore));
    }

    private static ResultWithData<Map<Identifier, Double>> commit(PreparedUse use, AbilityAttachment abilities, long gameTime) {
        if (AbilityStorage.onCooldown(abilities, use.ability(), gameTime))
            return ResultWithData.failure(Map.of(), Failures.COOLDOWN, null);
        CostPayment.PaymentResult payment = use.payment().pay();
        if (!payment.isSuccess()) return ResultWithData.failure(Map.of(), payment.failure(), payment.failedResource());
        applyAbilityState(use, abilities, gameTime);
        return ResultWithData.success(payment.resources());
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
            return AbilityFailure.CARRIED_NOT_INSTANT;
        if (!definition.condition().test(actor, context)) return Failures.CONDITION_FAILED;
        ResultWithData<PreparedUse> prepared = prepare(ability, abilities, gameTime, context,
                actor instanceof LivingEntity living ? living : null, false, payment);
        return prepared.isSuccess() ? null : prepared.failure();
    }

    private static UseResult use(Holder<Ability> ability, Entity actor,
                                 AbilityAttachment abilities, ResourceHolderAttachment resources, long gameTime,
                                 FormulaContext context, boolean requiresGrant, @Nullable Vec3 origin) {
        // Every use is authoritative: paying, writing state and running actions belong to the server, and a client
        // that asked anyway gets the same answer the script boundary gives.
        if (actor.level().isClientSide()) return UseResult.rejected(Failures.SERVER_ONLY, null);
        Ability definition = ability.value();
        if (actor instanceof LivingEntity living) {
            context = FormulaContexts.forEntity(living, context);
            context = withAbilityScaling(living, ability, context);
            if (!definition.elementAffinity().isEmpty() && context.value(DamageCalculationService.ELEMENT_MODIFIER) <= 0.0D)
                return UseResult.rejected(AbilityFailure.ELEMENT_AFFINITY, null);
        }
        // A carried ability must take effect at once: a cast is finished by walking the abilities the actor
        // *holds*, and a channel re-checks that grant on every pulse, so an item-granted one would never finish
        // its cast or would stop on its first tick. Refused here, before anything has been paid for.
        if (!requiresGrant && (definition.castTime().evaluate(context) > 0.0D
                || definition.type() instanceof ChannelSource))
            return UseResult.rejected(AbilityFailure.CARRIED_NOT_INSTANT, null);
        if (NeoForge.EVENT_BUS.post(new AbilityUseEvent.Pre(actor, ability, context)).isCanceled()) {
            return UseResult.rejected(Failures.CANCELLED, null);
        }
        if (!definition.condition().test(actor, context)) {
            return UseResult.rejected(Failures.CONDITION_FAILED, null);
        }
        if (!validateWord(definition, actor, context))
            return UseResult.rejected(AbilityFailure.PERMISSION_DENIED, null);
        ResultWithData<PreparedUse> prepared = prepare(ability, abilities, gameTime, context, actor instanceof LivingEntity living ? living : null, requiresGrant, null);
        if (!prepared.isSuccess()) return UseResult.rejected(prepared.failure(), prepared.failedResource());
        if (prepared.getData().castTimeTicks() > 0L) {
            AbilityStorage.value(abilities, ability, CastDeadline.class, new CastDeadline(CastDeadline.NO_CAST), gameTime)
                    .start(Math.addExact(gameTime, prepared.getData().castTimeTicks()));
            return UseResult.castingResult();
        }
        return finishPreparedUse(prepared.getData(), definition, actor, abilities, resources, gameTime, context, origin);
    }

    public static UseResult finishCast(Holder<Ability> ability, Entity actor,
                                       AbilityAttachment abilities, ResourceHolderAttachment resources, long gameTime,
                                       FormulaContext context) {
        if (actor.level().isClientSide()) return UseResult.rejected(Failures.SERVER_ONLY, null);
        Ability definition = ability.value();
        if (actor instanceof LivingEntity living) {
            context = FormulaContexts.forEntity(living, context);
            context = withAbilityScaling(living, ability, context);
            if (!definition.elementAffinity().isEmpty() && context.value(DamageCalculationService.ELEMENT_MODIFIER) <= 0.0D)
                return UseResult.rejected(AbilityFailure.ELEMENT_AFFINITY, null);
        }
        if (!AbilityStorage.castDue(abilities, ability, gameTime)) {
            return UseResult.castingResult();
        }
        AbilityStorage.clearCast(abilities, ability, gameTime);
        if (!definition.condition().test(actor, context)) return UseResult.rejected(Failures.CONDITION_FAILED, null);
        if (!validateWord(definition, actor, context))
            return UseResult.rejected(AbilityFailure.PERMISSION_DENIED, null);
        // Already started by something that could start one, so the grant it was approved under is not asked for
        // a second time.
        ResultWithData<PreparedUse> prepared = prepare(ability, abilities, gameTime, context,
                actor instanceof LivingEntity living ? living : null, false, null);
        if (!prepared.isSuccess()) return UseResult.rejected(prepared.failure(), prepared.failedResource());
        return finishPreparedUse(prepared.getData(), definition, actor, abilities, resources, gameTime, context, null);
    }

    private static UseResult finishPreparedUse(PreparedUse preparedUse, Ability definition, Entity actor,
                                               AbilityAttachment abilities, ResourceHolderAttachment resources, long gameTime,
                                               FormulaContext context, @Nullable Vec3 origin) {
        // Decided before the press pays for anything: a reach that lands on nobody is a refusal, not a paid silence.
        Failure unreached = applierFailure(definition, actor, context, origin);
        if (unreached != null) return UseResult.rejected(unreached, null);
        Pre resourceEvent = new Pre(resources, preparedUse.payment().resources());
        if (NeoForge.EVENT_BUS.post(resourceEvent).isCanceled()) return UseResult.rejected(Failures.CANCELLED, null);
        // The event owns the amounts from here on: the payment being made is the one it handed back.
        Optional<Failure> edited = preparedUse.payment().replaceResourceAmounts(resourceEvent.getAmounts());
        if (edited.isPresent()) return UseResult.rejected(edited.get(), null);
        ResultWithData<Map<Identifier, Double>> committed = commit(preparedUse, abilities, gameTime);
        if (!committed.isSuccess()) return UseResult.rejected(committed.failure(), committed.failedResource());
        if (definition.type() instanceof ChannelSource) {
            abilities.setChannelledAbility(preparedUse.ability());
            AbilityStorage.value(abilities, preparedUse.ability(), ChannelPulse.class, new ChannelPulse(0.0D), gameTime)
                    .set(Math.addExact(gameTime, preparedUse.channelIntervalTicks()));
        }
        // A channel owns the ability until released, so it never runs the one-shot entity action; its
        // target action still fires on activation and then once per upkeep pulse.
        executeEffects(definition, actor, context, origin);
        NeoForge.EVENT_BUS.post(new Post(resources, committed.getData()));
        NeoForge.EVENT_BUS.post(new AbilityUseEvent.Post(actor, preparedUse.ability(), context, committed.getData()));
        if (actor instanceof ServerPlayer player)
            MxtCriteriaTriggers.ABILITY.get().trigger(player, HolderHelper.id(preparedUse.ability()));
        return UseResult.committed(committed.getData());
    }

    // What one press costs when the press is not a cast: the condition, the cooldown and the entry's own costs,
    // paid once, in the same transaction a cast uses. A type that acts on a press calls this first unless it has
    // a reason of its own not to (see Toggable#gated).
    public static Result gate(ToggleContext context) {
        Holder<Ability> ability = context.ability();
        LivingEntity holder = context.holder();
        if (holder.level().isClientSide()) return Result.failure(Failures.SERVER_ONLY, null);
        AbilityAttachment abilities = holder.getData(MxtAttachments.ABILITY_HOLDER);
        long gameTime = holder.level().getGameTime();
        FormulaContext formula = context.formula();
        Ability definition = ability.value();
        if (!abilities.has(ability)) return Result.failure(AbilityFailure.NOT_GRANTED, null);
        if (AbilityStorage.onCooldown(abilities, ability, gameTime))
            return Result.failure(Failures.COOLDOWN, null);
        if (!definition.condition().test(holder, formula)) return Result.failure(Failures.CONDITION_FAILED, null);
        double cooldown = cooldownOf(ability, formula);
        if (!Double.isFinite(cooldown) || cooldown < 0.0D) return Result.failure(Failures.INVALID_FORMULA, null);
        CostPayment plan = CostPayment.of(CostContext.of(holder, formula, CostOrigin.ABILITY));
        Optional<Failure> refusal = plan.price(definition.costs());
        if (refusal.isPresent()) return Result.failure(refusal.get(), null);
        CostPayment.PaymentResult payment = plan.pay();
        if (!payment.isSuccess()) return Result.failure(payment.failure(), payment.failedResource());
        if (cooldown > 0.0D)
            AbilityStorage.startCooldown(abilities, ability, cooldown, gameTime);
        return Result.success();
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
                return ChannelResult.stopped(AbilityFailure.ELEMENT_AFFINITY);
            }
        }
        if (abilities.channelledAbility().filter(ability::equals).isEmpty())
            return ChannelResult.inactive();
        if (!abilities.has(ability) || !(definition.type() instanceof ChannelSource channel)) {
            stopChannel(abilities);
            return ChannelResult.stopped(AbilityFailure.NOT_GRANTED);
        }
        long nextTick = Math.round(AbilityStorage.get(abilities, ability, ChannelPulse.class)
                .map(ChannelPulse::nextTick).orElse((double) gameTime));
        if (gameTime < nextTick) return ChannelResult.waiting(nextTick);
        if (!definition.condition().test(actor, context)) {
            stopChannel(abilities);
            return ChannelResult.stopped(Failures.CONDITION_FAILED);
        }
        double interval = channel.channelInterval().evaluate(context);
        if (!Double.isFinite(interval) || interval <= 0.0D || interval > Long.MAX_VALUE) {
            stopChannel(abilities);
            return ChannelResult.stopped(Failures.INVALID_FORMULA);
        }
        CostContext upkeepContext = CostContext.of(actor instanceof LivingEntity living ? living : null, context, CostOrigin.CHANNEL_UPKEEP);
        CostPayment upkeep = CostPayment.of(upkeepContext);
        Optional<Failure> unpaid = upkeep.price(channel.upkeepCosts());
        if (unpaid.isPresent()) {
            stopChannel(abilities);
            return ChannelResult.stopped(unpaid.get());
        }
        // The event owns the amounts from here on: the pulse being paid is the one it handed back.
        Pre resourceEvent = new Pre(resources, upkeep.resources());
        if (NeoForge.EVENT_BUS.post(resourceEvent).isCanceled()) {
            stopChannel(abilities);
            return ChannelResult.stopped(Failures.CANCELLED);
        }
        Optional<Failure> edited = upkeep.replaceResourceAmounts(resourceEvent.getAmounts());
        if (edited.isPresent()) {
            stopChannel(abilities);
            return ChannelResult.stopped(edited.get());
        }
        CostPayment.PaymentResult payment = upkeep.pay();
        if (!payment.isSuccess()) {
            stopChannel(abilities);
            return ChannelResult.stopped(payment.failure());
        }
        // A channel is never carried by an item (see useCarried), so its pulses happen where the actor is.
        executeEffects(definition, actor, context, null);
        NeoForge.EVENT_BUS.post(new Post(resources, payment.resources()));
        long intervalTicks = Math.max(1L, Math.round(interval));
        long followingTick = Math.addExact(gameTime, intervalTicks);
        AbilityStorage.value(abilities, ability, ChannelPulse.class, new ChannelPulse(0.0D), gameTime).set(followingTick);
        return ChannelResult.pulsed(followingTick, payment.resources());
    }

    public static boolean stopChannel(AbilityAttachment abilities) {
        if (abilities.channelledAbility().isEmpty()) return false;
        abilities.setChannelledAbility(null);
        return true;
    }

    // Clears a pending cast without touching resources, cooldowns or unrelated stored state.
    public static boolean cancelCast(Holder<Ability> ability, AbilityAttachment abilities, long gameTime) {
        if (!AbilityStorage.hasCast(abilities, ability)) return false;
        AbilityStorage.clearCast(abilities, ability, gameTime);
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
        if (!(applier.payload().value().type() instanceof ActionCarrier)) return Failures.NOT_APPLICABLE;
        try {
            return applier.reach(actor, context, origin).findAny().isPresent() ? null : Failures.NO_TARGET;
        } catch (RuntimeException exception) {
            // A selector or condition that cannot be evaluated is a broken definition, not an empty reach.
            LOGGER.log("Ability target selection failed", exception);
            return Failures.INVALID_FORMULA;
        }
    }

    private static void applyAbilityState(PreparedUse use, AbilityAttachment abilities, long gameTime) {
        Holder<Ability> ability = use.ability();
        AbilityStorage.startCooldown(abilities, ability, use.cooldownTicks(), gameTime);
        if (use.consumeCharge()) {
            ChargesDataStorage declaration = ability.value().charges()
                    .map(ChargesDataStorage.Settings::declared).orElse(ChargesDataStorage.INSTANCE);
            AbilityStorage.charges(abilities, ability, declaration, gameTime).setRemaining(Math.max(0.0D, use.chargeBefore() - 1.0D), gameTime);
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
        return scaled.with(DamageCalculationService.DAMAGE_MULTIPLIER, ProgressionDamageMultiplier.of(actor, ability));
    }

    public record PreparedUse(Holder<Ability> ability, CostPayment payment, long castTimeTicks,
                              long cooldownTicks, long channelIntervalTicks,
                              boolean consumeCharge, double chargeBefore) {
    }

    public static final class UseResult extends Result {
        private final boolean casting;
        private final Map<Identifier, Double> amounts;

        private UseResult(boolean casting, @Nullable Failure failure, @Nullable Identifier failedResource,
                          Map<Identifier, Double> amounts) {
            super(failure, failedResource);
            this.casting = casting;
            this.amounts = amounts;
        }

        private static UseResult committed(Map<Identifier, Double> amounts) {
            return new UseResult(false, null, null, amounts);
        }

        private static UseResult castingResult() {
            return new UseResult(true, null, null, Map.of());
        }

        public static UseResult rejected(Failure failure, Identifier resource) {
            return new UseResult(false, failure, resource, Map.of());
        }

        public boolean casting() {
            return this.casting;
        }

        public Map<Identifier, Double> amounts() {
            return this.amounts;
        }
    }

    public static final class ChannelResult extends Result {
        private final State state;
        private final long nextTick;
        private final Map<Identifier, Double> amounts;

        private ChannelResult(State state, @Nullable Failure failure, long nextTick,
                              Map<Identifier, Double> amounts) {
            super(failure, null);
            this.state = state;
            this.nextTick = nextTick;
            this.amounts = amounts;
        }

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

        public State state() {
            return this.state;
        }

        public long nextTick() {
            return this.nextTick;
        }

        public Map<Identifier, Double> amounts() {
            return this.amounts;
        }
    }

    public enum State {INACTIVE, WAITING, PULSED, STOPPED}
}