package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.CurseHolderAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.ability.AbilityComponent;
import com.iafenvoy.mxt.data.ability.component.ChargesAbilityComponent;
import com.iafenvoy.mxt.data.ability.component.CooldownAbilityComponent;
import com.iafenvoy.mxt.data.ability.AbilityComponentState;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.type.ChannelledAbilityType;
import com.iafenvoy.mxt.data.ability.type.CompositeAbilityType;
import com.iafenvoy.mxt.data.ability.type.WordAbilityType;
import com.iafenvoy.mxt.data.ability.type.WordAbilityType.WordEffect;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.ItemCost;
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
import com.iafenvoy.mxt.runtime.resource.ResourceService;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

    public static PrepareResult prepare(@NotNull Holder<Ability> ability, Ability definition, AbilityAttachment abilities,
                                        ResourceHolderAttachment resources, long gameTime, FormulaContext context) {
        return prepare(ability, definition, abilities, resources, gameTime, context, null);
    }

    private static PrepareResult prepare(@NotNull Holder<Ability> ability, Ability definition, AbilityAttachment abilities,
                                         ResourceHolderAttachment resources, long gameTime, FormulaContext context,
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
        Evaluation costs = evaluateResourceCosts(definition.costs(), payer, context);
        if (!definition.costs().isEmpty()) {
            if (requiresPlayerCost(definition.costs()) && !(payer instanceof Player))
                return PrepareResult.rejected(Failure.INSUFFICIENT_COST, null);
            if (payer instanceof Player player && definition.costs().stream().anyMatch(cost -> !cost.check(player)))
                return PrepareResult.rejected(Failure.INSUFFICIENT_COST, null);
        }
        Result preview = ResourceTransactions.tryConsume(resources.copy(), costs);
        if (!preview.committed())
            return PrepareResult.rejected(Failure.INSUFFICIENT_RESOURCE, preview.failedResource());
        return PrepareResult.prepared(new PreparedUse(ability, costs, definition.costs(), Math.round(castTime), Math.round(cooldown), channelInterval, charges.isPresent(), chargeBefore));
    }

    /**
     * Commits cost, component state and cooldown as one server-thread operation.
     */
    public static CommitResult commit(PreparedUse use, AbilityAttachment abilities, ResourceHolderAttachment resources, long gameTime) {
        return commit(use, abilities, resources, gameTime, null);
    }

    private static CommitResult commit(PreparedUse use, AbilityAttachment abilities, ResourceHolderAttachment resources, long gameTime, Player player) {
        if (abilities.isOnCooldown(use.ability(), gameTime)) return CommitResult.rejected(Failure.COOLDOWN, null);
        if (requiresPlayerCost(use.costsList()) && player == null)
            return CommitResult.rejected(Failure.INSUFFICIENT_COST, null);
        if (player != null && use.costsList().stream().anyMatch(cost -> !cost.check(player)))
            return CommitResult.rejected(Failure.INSUFFICIENT_COST, null);
        Result payment = ResourceTransactions.tryConsume(resources, use.costs);
        if (!payment.committed()) return CommitResult.rejected(Failure.INSUFFICIENT_RESOURCE, payment.failedResource());
        if (player != null) use.costsList().stream()
                .filter(cost -> !(cost instanceof com.iafenvoy.mxt.data.cost.ResourceCost))
                .forEach(cost -> cost.consume(player));
        abilities.setCooldownUntil(use.ability(), Math.addExact(gameTime, use.cooldownTicks));
        abilities.setComponentState(use.ability(), "cooldown_duration", AbilityComponentState.initial(use.cooldownTicks(), gameTime));
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
                                AbilityAttachment abilities, ResourceHolderAttachment resources, long gameTime,
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
                                       AbilityAttachment abilities, ResourceHolderAttachment resources, long gameTime,
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
                                               AbilityAttachment abilities, ResourceHolderAttachment resources, long gameTime,
                                               FormulaContext context) {
        Pre resourceEvent = new Pre(resources, preparedUse.costs().amounts());
        if (NeoForge.EVENT_BUS.post(resourceEvent).isCanceled()) return UseResult.rejected(Failure.CANCELLED, null);
        PreparedUse adjustedUse = new PreparedUse(preparedUse.ability(), new Evaluation(resourceEvent.amounts()), preparedUse.costsList(),
                preparedUse.castTimeTicks(), preparedUse.cooldownTicks(), preparedUse.channelIntervalTicks(), preparedUse.consumeCharge(), preparedUse.chargeBefore());
        CommitResult committed = commit(adjustedUse, abilities, resources, gameTime, actor instanceof Player player ? player : null);
        if (!committed.committed()) return UseResult.rejected(committed.failure(), committed.failedResource());
        if (definition.type() instanceof ChannelledAbilityType) {
            abilities.setChannelledAbility(preparedUse.ability());
            abilities.setComponentState(preparedUse.ability(), "channel_next_tick", AbilityComponentState.initial(Math.addExact(gameTime, adjustedUse.channelIntervalTicks()), gameTime));
        } else {
            executeEffects(definition, actor, context);
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
                                            AbilityAttachment abilities, ResourceHolderAttachment resources, long gameTime,
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
        executeEffects(definition, actor, context);
        NeoForge.EVENT_BUS.post(new Post(resources, payment.amounts()));
        long intervalTicks = Math.max(1L, Math.round(interval));
        long followingTick = Math.addExact(gameTime, intervalTicks);
        abilities.setComponentState(ability, "channel_next_tick", AbilityComponentState.initial(followingTick, gameTime));
        return ChannelResult.pulsed(followingTick, payment.amounts());
    }

    public static boolean stopChannel(AbilityAttachment abilities) {
        if (abilities.channelledAbility().isEmpty()) return false;
        abilities.setChannelledAbility(null);
        return true;
    }

    /**
     * Clears a pending cast without touching resources, cooldowns or unrelated component state.
     */
    public static boolean cancelCast(Holder<Ability> ability, AbilityAttachment abilities, long gameTime) {
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
        if (effect != WordEffect.SELF_HEAL) return true;
        try {
            double amount = amount1.evaluate(context);
            return Double.isFinite(amount) && amount >= 0.0D && amount <= Float.MAX_VALUE;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static void executeEffects(Ability definition, Entity actor, FormulaContext context) {
        try {
            if (definition.type() instanceof ChannelledAbilityType) return;
            if (definition.type() instanceof WordAbilityType word) {
                executeWord(word, actor, context);
                return;
            }
            definition.entityAction().execute(actor, context);
        } catch (RuntimeException exception) {
            MiXianTu.LOGGER.error("Ability entity action failed", exception);
        }
        executeTargetAction(definition, actor, context);
    }

    private static void executeWord(WordAbilityType word, Entity actor, FormulaContext context) {
        if (word.effect() == WordEffect.SELF_HEAL && actor instanceof LivingEntity living) {
            living.heal((float) word.amount().evaluate(context));
        } else if (word.effect() == WordEffect.PURGE_SELF_CURSES) {
            CurseHolderAttachment holder = actor.getData(MxtAttachments.CURSE_HOLDER);
            new LinkedList<>(holder.instances().keySet()).forEach(curse ->
                    CurseService.remove(actor, curse, Reason.EXPLICIT, -1L));
        }
    }

    /**
     * Validates every required child against detached drafts, then commits all costs before running
     * any action. World actions are deliberately never rolled back.
     */
    public static UseResult useComposite(Holder<Ability> composite, Ability compositeDefinition, Entity actor,
                                         AbilityAttachment abilities, ResourceHolderAttachment resources, long gameTime,
                                         FormulaContext context) {
        if (!(compositeDefinition.type() instanceof CompositeAbilityType(
                List<Holder<Ability>> abilities1, boolean allRequired
        )))
            return UseResult.rejected(Failure.INVALID_FORMULA, null);
        if (!allRequired) {
            if (abilities1.isEmpty()) return UseResult.rejected(Failure.NOT_GRANTED, null);
            Holder<Ability> child = abilities1.getFirst();
            return use(child, child.value(), actor, abilities, resources, gameTime, context);
        }

        Player player = actor instanceof Player value ? value : null;
        AbilityAttachment abilityDraft = abilities.copy();
        ResourceHolderAttachment resourceDraft = resources.copy();
        ItemCostDraft itemDraft = player == null ? null : new ItemCostDraft(player);
        List<CompositeStep> steps = new LinkedList<>();
        LinkedHashMap<Identifier, Double> paid = new LinkedHashMap<>();
        for (Holder<Ability> childHolder : abilities1) {
            Ability child = childHolder.value();
            FormulaContext childContext = context;
            if (actor instanceof LivingEntity living) {
                childContext = withElementAffinity(living, child, FormulaContexts.forEntity(living, context.variables()));
                if (!child.elementAffinity().isEmpty() && childContext.value("element_modifier") <= 0.0D)
                    return UseResult.rejected(Failure.ELEMENT_AFFINITY, null);
            }
            if (NeoForge.EVENT_BUS.post(new AbilityUseEvent.Pre(actor, HolderHelper.id(childHolder), child, childContext)).isCanceled())
                return UseResult.rejected(Failure.CANCELLED, null);
            if (!child.condition().test(actor, childContext)) return UseResult.rejected(Failure.CONDITION_FAILED, null);
            if (!validateWord(child, actor, childContext)) return UseResult.rejected(Failure.PERMISSION_DENIED, null);
            PrepareResult prepared = prepare(childHolder, child, abilityDraft, resourceDraft, gameTime, childContext,
                    actor instanceof LivingEntity living ? living : null);
            if (!prepared.approved()) return UseResult.rejected(prepared.failure(), prepared.failedResource());
            if (prepared.use().castTimeTicks() > 0L)
                return UseResult.rejected(Failure.INVALID_FORMULA, null);
            if (!reserveItemCosts(prepared.use().costsList(), player, itemDraft))
                return UseResult.rejected(Failure.INSUFFICIENT_COST, null);
            Pre resourceEvent = new Pre(resources, prepared.use().costs().amounts());
            if (NeoForge.EVENT_BUS.post(resourceEvent).isCanceled()) return UseResult.rejected(Failure.CANCELLED, null);
            PreparedUse adjusted = withCosts(prepared.use(), new Evaluation(resourceEvent.amounts()));
            Result preview = ResourceTransactions.tryConsume(resourceDraft, adjusted.costs());
            if (!preview.committed())
                return UseResult.rejected(Failure.INSUFFICIENT_RESOURCE, preview.failedResource());
            applyAbilityState(adjusted, abilityDraft, gameTime);
            adjusted.costs().amounts().forEach((id, amount) -> paid.merge(id, amount, Double::sum));
            steps.add(new CompositeStep(childHolder, child, adjusted, childContext));
        }
        for (CompositeStep step : steps) {
            CommitResult committed = commit(step.use(), abilities, resources, gameTime, player);
            if (!committed.committed()) {
                MiXianTu.LOGGER.error("Composite ability {} failed after prevalidation: {}", HolderHelper.id(composite), committed.failure());
                return UseResult.rejected(committed.failure(), committed.failedResource());
            }
        }
        for (CompositeStep step : steps) {
            if (step.definition().type() instanceof ChannelledAbilityType) {
                abilities.setChannelledAbility(step.use().ability());
                abilities.setComponentState(step.use().ability(), "channel_next_tick",
                        AbilityComponentState.initial(Math.addExact(gameTime, step.use().channelIntervalTicks()), gameTime));
            } else {
                executeEffects(step.definition(), actor, step.context());
            }
            NeoForge.EVENT_BUS.post(new Post(resources, step.use().costs().amounts()));
            NeoForge.EVENT_BUS.post(new AbilityUseEvent.Post(actor, HolderHelper.id(step.ability()), step.definition(), step.context(), step.use().costs().amounts()));
            if (actor instanceof ServerPlayer serverPlayer)
                MxtCriteriaTriggers.ABILITY.get().trigger(serverPlayer, HolderHelper.id(step.ability()));
        }
        return UseResult.committed(paid);
    }

    private static PreparedUse withCosts(PreparedUse use, Evaluation costs) {
        return new PreparedUse(use.ability(), costs, use.costsList(), use.castTimeTicks(), use.cooldownTicks(),
                use.channelIntervalTicks(), use.consumeCharge(), use.chargeBefore());
    }

    private static void applyAbilityState(PreparedUse use, AbilityAttachment abilities, long gameTime) {
        abilities.setCooldownUntil(use.ability(), Math.addExact(gameTime, use.cooldownTicks()));
        abilities.setComponentState(use.ability(), "cooldown_duration", AbilityComponentState.initial(use.cooldownTicks(), gameTime));
        if (use.consumeCharge())
            abilities.setComponentState(use.ability(), "charges", AbilityComponentState.initial(Math.max(0.0D, use.chargeBefore() - 1.0D), gameTime));
    }

    private static boolean reserveItemCosts(List<Cost> costs, Player player, ItemCostDraft draft) {
        if (requiresPlayerCost(costs) && (player == null || draft == null)) return false;
        for (Cost cost : costs) {
            if (cost instanceof com.iafenvoy.mxt.data.cost.ResourceCost) continue;
            if (!(cost instanceof ItemCost itemCost) || !draft.reserve(itemCost, player)) return false;
        }
        return true;
    }

    private static Evaluation evaluateResourceCosts(List<Cost> costs, LivingEntity payer, FormulaContext context) {
        LinkedHashMap<Identifier, Double> amounts = new LinkedHashMap<>();
        for (Cost cost : costs) {
            if (!(cost instanceof com.iafenvoy.mxt.data.cost.ResourceCost resourceCost)) continue;
            Identifier id = resourceCost.id();
            FormulaContext costContext = payer == null ? context
                    : ResourceService.formulaContext(payer, id, resourceCost.resource().value(), context);
            double amount = resourceCost.evaluate(costContext);
            if (amounts.put(id, amount) != null)
                throw new IllegalArgumentException("Duplicate resource cost " + id);
        }
        return new Evaluation(amounts);
    }

    /**
     * Resource-only costs can run without a Player; item and other costs still require one.
     */
    private static boolean requiresPlayerCost(List<Cost> costs) {
        return costs.stream().anyMatch(cost -> !(cost instanceof com.iafenvoy.mxt.data.cost.ResourceCost));
    }

    private static FormulaContext withElementAffinity(LivingEntity actor, Ability definition, FormulaContext context) {
        if (definition.elementAffinity().isEmpty()) return context;
        double modifier = CultivationAffinity.abilityMultiplier(actor.getData(MxtAttachments.SPIRIT_IDENTITY), definition.elementAffinity(), context,
                id -> MxtDatapackRegistries.get(MxtResourceKeys.SPIRIT_ROOT, id));
        LinkedHashMap<String, Double> variables = new LinkedHashMap<>(context.variables());
        variables.put("element_modifier", modifier);
        return new FormulaContext(variables, context.random(), context.player());
    }

    private static <T extends AbilityComponent> Optional<T> component(Ability definition, Class<T> type) {
        return definition.components().stream().filter(type::isInstance).map(type::cast).findFirst();
    }

    /**
     * Applies a bi-entity action to each selected target. One failing action never prevents the
     * remaining targets from receiving their action.
     */
    private static void executeTargetAction(Ability definition, Entity actor, FormulaContext context) {
        try {
            definition.targetSelector().select(actor, context).forEach(target -> executeTargetAction(definition, actor, target, context));
        } catch (RuntimeException exception) {
            MiXianTu.LOGGER.error("Ability target selection failed", exception);
        }
    }

    public static void executeTargetAction(Ability definition, Entity actor, Entity target, FormulaContext context) {
        try {
            FormulaContext targetContext = actor instanceof LivingEntity caster && target instanceof LivingEntity livingTarget
                    ? FormulaContexts.forEntities(caster, livingTarget, context.variables()) : context;
            if (definition.targetCondition().test(actor, target, targetContext))
                definition.biEntityAction().execute(actor, target, targetContext);
        } catch (RuntimeException exception) {
            MiXianTu.LOGGER.error("Ability target action failed", exception);
        }
    }

    private static final class ItemCostDraft {
        private final Map<Integer, Integer> remaining = new LinkedHashMap<>();

        private ItemCostDraft(Player player) {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
                this.remaining.put(slot, player.getInventory().getItem(slot).getCount());
        }

        private boolean reserve(ItemCost cost, Player player) {
            int required = cost.required(player);
            if (required <= 0) return false;
            for (int slot = 0; slot < player.getInventory().getContainerSize() && required > 0; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                boolean matches = cost.matcher().entries().stream().anyMatch(entry -> entry.matches(stack));
                if (!matches) continue;
                int available = this.remaining.getOrDefault(slot, 0);
                int used = Math.min(required, available);
                this.remaining.put(slot, available - used);
                required -= used;
            }
            return required == 0;
        }
    }

    private record CompositeStep(Holder<Ability> ability, Ability definition, PreparedUse use, FormulaContext context) {
    }

    public enum Failure {DISABLED, NOT_GRANTED, COOLDOWN, INSUFFICIENT_RESOURCE, INSUFFICIENT_COST, INVALID_FORMULA, CONDITION_FAILED, NO_CHARGES, CANCELLED, PERMISSION_DENIED, ELEMENT_AFFINITY, SERVER_ONLY}

    public record PreparedUse(Holder<Ability> ability, Evaluation costs, List<Cost> costsList, long castTimeTicks,
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
