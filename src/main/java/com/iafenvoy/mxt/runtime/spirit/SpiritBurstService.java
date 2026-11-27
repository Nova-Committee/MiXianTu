package com.iafenvoy.mxt.runtime.spirit;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.iafenvoy.mxt.api.AuraHandler;
import com.iafenvoy.mxt.attachment.SpiritBurstCooldownAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.condition.builtin.entity.HasRealmEntityCondition;
import com.iafenvoy.mxt.entity.SpiritBurstEntity;
import com.iafenvoy.mxt.item.SpiritVesselItem;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtCapabilities;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.MxtProfilerSources;
import com.iafenvoy.mxt.runtime.resource.ResourceUseService;
import com.iafenvoy.mxt.runtime.resulting.FailureSources;
import com.iafenvoy.mxt.runtime.resulting.MessageFailure;
import com.iafenvoy.saturn.attachment.ResourceHolderAttachment;
import com.iafenvoy.saturn.data.resource.Resource;
import com.iafenvoy.saturn.profiling.ProfilerScope;
import com.iafenvoy.saturn.profiling.ServerProfiling;
import com.iafenvoy.saturn.registry.SaturnAttachments;
import com.iafenvoy.saturn.runtime.resource.ResourceService;
import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.saturn.runtime.resulting.FailureSource;
import com.iafenvoy.saturn.runtime.resulting.Failures;
import com.iafenvoy.saturn.runtime.resulting.Result;
import com.iafenvoy.saturn.util.DefinitionText;
import com.iafenvoy.saturn.util.TooltipText;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side hold-to-fire controller for aura-defined spirit bursts. Any living entity can hold one; a key or the
 * wheel is only how a player asks for it.
 */
@EventBusSubscriber
public final class SpiritBurstService {
    public static final long FIRE_INTERVAL_TICKS = 10L;
    // Keyed by aura rather than id: only the id arrives, because a key binding travels as a name.
    private static final SetMultimap<UUID, Holder<Aura>> ACTIVE_AURAS = HashMultimap.create();

    private SpiritBurstService() {
    }

    public static void setFiring(LivingEntity holder, Optional<Identifier> auraId, boolean firing) {
        try (ProfilerScope _ = ServerProfiling.scope(MxtProfilerSources.SPIRIT)) {
            UUID holderId = holder.getUUID();
            if (auraId.isEmpty()) {
                if (!firing) ACTIVE_AURAS.removeAll(holderId);
                return;
            }
            Optional<Holder<Aura>> valid = auraId.flatMap(id -> MxtDatapackRegistries.holder(holder.level().registryAccess(), MxtResourceKeys.AURA, id))
                    .filter(aura -> aura.value().auraType().isPresent() && ResourceUseService.canUse(holder, aura));
            if (valid.isEmpty()) return;
            Set<Holder<Aura>> active = ACTIVE_AURAS.get(holderId);
            if (firing) {
                boolean added = active.add(valid.get());
                if (added) fire(holder, valid.get());
            } else {
                active.remove(valid.get());
                if (active.isEmpty()) ACTIVE_AURAS.removeAll(holderId);
            }
        }
    }

    // Every living entity ticks here, so the attachment is only touched once it exists or a burst is held: getData
    // would otherwise hand an empty cooldown table to every mob in the level.
    @SubscribeEvent
    public static void onEntityTick(Post event) {
        try (ProfilerScope _ = ServerProfiling.scope(MxtProfilerSources.SPIRIT)) {
            if (!(event.getEntity() instanceof LivingEntity holder) || holder.level().isClientSide()) return;
            Set<Holder<Aura>> active = ACTIVE_AURAS.get(holder.getUUID());
            if (active.isEmpty() && !holder.hasData(MxtAttachments.SPIRIT_BURST_COOLDOWNS.get()))
                return;
            SpiritBurstCooldownAttachment cooldowns = holder.getData(MxtAttachments.SPIRIT_BURST_COOLDOWNS);
            cooldowns.clearExpired(holder.level().getGameTime());
            if (active.isEmpty()) return;
            for (Holder<Aura> aura : Set.copyOf(active)) fire(holder, aura);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        ACTIVE_AURAS.removeAll(event.getEntity().getUUID());
    }

    public static boolean fireOnce(LivingEntity holder, Identifier auraId) {
        return fireOnceResult(holder, auraId) instanceof Result.SuccessResult<?>;
    }

    public static Result<Void> fireOnceResult(LivingEntity holder, Identifier auraId) {
        try (ProfilerScope _ = ServerProfiling.scope(MxtProfilerSources.SPIRIT)) {
            if (holder.level().isClientSide()) return Result.failure(Failures.SERVER_ONLY);
            if (auraId == null) return Result.failure(Refusal.UNKNOWN_AURA);
            Holder<Aura> aura = MxtDatapackRegistries.holder(holder.level().registryAccess(), MxtResourceKeys.AURA, auraId).orElse(null);
            return aura == null ? Result.failure(Refusal.UNKNOWN_AURA) : attempt(holder, aura);
        }
    }

    private static void fire(LivingEntity holder, Holder<Aura> aura) {
        Set<Holder<Aura>> active = ACTIVE_AURAS.get(holder.getUUID());
        if (!active.contains(aura)) return;
        active.removeIf(candidate -> candidate.value().auraType().isEmpty() || !ResourceUseService.canUse(holder, candidate));
        attempt(holder, aura);
    }

    // The checks and the payment every firing shares, whether it was asked for once or held down.
    private static Result<Void> attempt(LivingEntity holder, Holder<Aura> aura) {
        if (holder.level().isClientSide()) return Result.failure(Failures.SERVER_ONLY);
        Aura definition = aura.value();
        if (definition.auraType().isEmpty()) return Result.failure(Refusal.MISSING_ELEMENT);
        if (!ResourceUseService.canUse(holder, aura)) {
            if (definition.useCondition() instanceof HasRealmEntityCondition(Holder<Aura> aura1)) {
                Component name = DefinitionText.nameOrId(aura1.value().resource());
                return Result.failure(new MessageFailure(Refusal.REALM_REQUIRED,
                        Component.translatable(Refusal.REALM_REQUIRED.key(), name)));
            }
            return Result.failure(Refusal.USE_CONDITION);
        }
        SpiritBurstCooldownAttachment cooldowns = holder.getData(MxtAttachments.SPIRIT_BURST_COOLDOWNS);
        long gameTime = holder.level().getGameTime();
        if (cooldowns.isOnCooldown(aura, gameTime)) return Result.failure(Failures.COOLDOWN);
        Result<Void> result = tryFire(holder, aura);
        if (result instanceof Result.SuccessResult<?>)
            cooldowns.setCooldownUntil(aura, Math.addExact(gameTime, FIRE_INTERVAL_TICKS));
        return result;
    }

    public static boolean validBurstAmount(double amount) {
        return Double.isFinite(amount) && amount > 0.0D;
    }

    private static Result<Void> tryFire(LivingEntity holder, Holder<Aura> aura) {
        Aura definition = aura.value();
        Holder<Resource> resource = definition.resource();
        FormulaContext context = ResourceService.formulaContext(holder, resource, FormulaContext.of(holder));
        double amount = definition.burstAmount().evaluate(context);
        if (!validBurstAmount(amount)) return Result.failure(Refusal.INVALID_AMOUNT);
        ResourceHolderAttachment resources = holder.getData(SaturnAttachments.RESOURCE_HOLDER);
        if (!ResourceService.initialize(resources, resource, context).valid())
            return Result.failure(Refusal.RESOURCE_UNAVAILABLE);
        double available = resources.get(resource);
        if (available < amount) return Result.failure(new MessageFailure(Refusal.INSUFFICIENT_BALANCE,
                Component.translatable(Refusal.INSUFFICIENT_BALANCE.key(), DefinitionText.nameOrId(resource),
                        TooltipText.number(available), TooltipText.number(amount))));
        ItemStack stack = holder.getMainHandItem();
        if (stack.getItem() instanceof SpiritVesselItem)
            return chargeVessel(holder, stack, aura, amount, resources, context);
        if (!ResourceService.change(resources, resource, -amount, context).valid())
            return Result.failure(Refusal.RESOURCE_UNAVAILABLE);
        holder.level().addFreshEntity(new SpiritBurstEntity(holder.level(), holder, aura, amount, resource.value().particleColor()));
        return Result.success();
    }

    private static Result<Void> chargeVessel(LivingEntity holder, ItemStack stack, Holder<Aura> aura, double amount,
                                             ResourceHolderAttachment resources, FormulaContext context) {
        MxtCapabilities.ItemContext slot = holder instanceof Player player
                ? MxtCapabilities.ItemContext.forHand(player, InteractionHand.MAIN_HAND)
                : MxtCapabilities.ItemContext.forStack(stack, holder);
        AuraHandler vessel = MxtCapabilities.findItem(stack, slot);
        if (vessel == null) return Result.failure(Refusal.VESSEL_REFUSED);
        double capacity = vessel.getCapacity(aura);
        if (capacity <= 0.0D) return Result.failure(Refusal.VESSEL_REFUSED);
        if (vessel.getAmount(aura) >= capacity) return Result.failure(Refusal.VESSEL_FULL);
        Holder<Resource> resource = aura.value().resource();
        try (Transaction transaction = Transaction.openRoot()) {
            if (AuraHandlerUtil.insert(vessel, aura, amount, false, false, transaction) <= 0.0D)
                return Result.failure(Refusal.VESSEL_REFUSED);
            if (!ResourceService.change(resources, resource, -amount, context).valid())
                return Result.failure(Refusal.RESOURCE_UNAVAILABLE);
            transaction.commit();
        }
        SpiritVesselItem.showCharge(holder, holder.getMainHandItem(), resource);
        return Result.success();
    }

    private enum Refusal implements Failure {
        UNKNOWN_AURA, MISSING_ELEMENT, REALM_REQUIRED, USE_CONDITION, INVALID_AMOUNT, INSUFFICIENT_BALANCE,
        RESOURCE_UNAVAILABLE, VESSEL_REFUSED, VESSEL_FULL;

        @Override
        public FailureSource source() {
            return FailureSources.AURA;
        }
    }
}
