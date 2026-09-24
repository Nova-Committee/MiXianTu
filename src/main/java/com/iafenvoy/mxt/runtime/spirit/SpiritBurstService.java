package com.iafenvoy.mxt.runtime.spirit;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.SpiritBurstCooldownAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.resource.ResourceUseService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;

import java.util.*;

/**
 * Server-side hold-to-fire controller for aura-defined spirit bursts. Any living entity can hold one; a key or the
 * wheel is only how a player asks for it.
 */
@EventBusSubscriber
public final class SpiritBurstService {
    public static final long FIRE_INTERVAL_TICKS = 10L;
    // Keyed by aura rather than id: only the id arrives, because a key binding travels as a name.
    private static final Map<UUID, Set<Holder<Aura>>> ACTIVE_AURAS = new HashMap<>();

    private SpiritBurstService() {
    }

    public static void setFiring(LivingEntity holder, Optional<Identifier> auraId, boolean firing) {
        UUID holderId = holder.getUUID();
        if (auraId.isEmpty()) {
            if (!firing) ACTIVE_AURAS.remove(holderId);
            return;
        }
        Optional<Holder<Aura>> valid = auraId.flatMap(id -> MxtDatapackRegistries.holder(MxtResourceKeys.AURA, id))
                .map(aura -> (Holder<Aura>) aura)
                .filter(aura -> Elements.enabled(aura.value().auraType()) && ResourceUseService.canUse(holder, aura));
        if (valid.isEmpty()) return;
        Set<Holder<Aura>> active = ACTIVE_AURAS.computeIfAbsent(holderId, ignored -> new HashSet<>());
        if (firing) {
            boolean added = active.add(valid.get());
            if (added) fire(holder, valid.get());
        } else {
            active.remove(valid.get());
            if (active.isEmpty()) ACTIVE_AURAS.remove(holderId);
        }
    }

    // Every living entity ticks here, so the attachment is only touched once it exists or a burst is held: getData
    // would otherwise hand an empty cooldown table to every mob in the level.
    @SubscribeEvent
    public static void onEntityTick(Post event) {
        if (!(event.getEntity() instanceof LivingEntity holder) || holder.level().isClientSide()) return;
        Set<Holder<Aura>> active = ACTIVE_AURAS.get(holder.getUUID());
        if ((active == null || active.isEmpty()) && !holder.hasData(MxtAttachments.SPIRIT_BURST_COOLDOWNS.get())) return;
        SpiritBurstCooldownAttachment cooldowns = holder.getData(MxtAttachments.SPIRIT_BURST_COOLDOWNS);
        cooldowns.clearExpired(holder.level().getGameTime());
        if (active == null || active.isEmpty()) return;
        for (Holder<Aura> aura : Set.copyOf(active)) fire(holder, aura);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        ACTIVE_AURAS.remove(event.getEntity().getUUID());
    }

    // Same gate, cooldown and payment as the held path, so the wheel cannot fire a burst in a situation where
    // a held key would have refused it. Returns whether a burst was actually fired.
    public static boolean fireOnce(LivingEntity holder, Identifier auraId) {
        if (auraId == null) return false;
        Holder<Aura> aura = MxtDatapackRegistries.holder(MxtResourceKeys.AURA, auraId)
                .map(value -> (Holder<Aura>) value)
                .filter(value -> Elements.enabled(value.value().auraType()) && ResourceUseService.canUse(holder, value))
                .orElse(null);
        return aura != null && attempt(holder, aura);
    }

    private static void fire(LivingEntity holder, Holder<Aura> aura) {
        Set<Holder<Aura>> active = ACTIVE_AURAS.get(holder.getUUID());
        if (active == null || !active.contains(aura)) return;
        active.removeIf(candidate -> !Elements.enabled(candidate.value().auraType()) || !ResourceUseService.canUse(holder, candidate));
        attempt(holder, aura);
    }

    // The checks and the payment every firing shares, whether it was asked for once or held down.
    private static boolean attempt(LivingEntity holder, Holder<Aura> aura) {
        ResourceHolderAttachment resources = holder.getData(MxtAttachments.RESOURCE_HOLDER);
        SpiritBurstCooldownAttachment cooldowns = holder.getData(MxtAttachments.SPIRIT_BURST_COOLDOWNS);
        if (cooldowns.isOnCooldown(aura, holder.level().getGameTime())) return false;
        if (!tryFire(holder, resources, aura)) return false;
        cooldowns.setCooldownUntil(aura, Math.addExact(holder.level().getGameTime(), FIRE_INTERVAL_TICKS));
        return true;
    }

    // A positive burst_amount marks an aura that can be fired by the shortcut.
    private static boolean tryFire(LivingEntity holder, ResourceHolderAttachment resources, Holder<Aura> aura) {
        Aura definition = aura.value();
        if (!Elements.enabled(definition.auraType()) || !ResourceUseService.canUse(holder, aura)) return false;
        Holder<Resource> resource = definition.resource();
        FormulaContext context = ResourceService.formulaContext(holder, resource, FormulaContext.of(holder));
        int amount = asWholeAmount(definition.burstAmount().evaluate(context));
        if (amount <= 0) return false;
        if (!ResourceService.initialize(resources, resource, context).valid() || resources.get(resource) < amount)
            return false;
        if (!ResourceService.change(resources, resource, -amount, context).valid()) return false;
        holder.level().addFreshEntity(new SpiritBurstEntity(holder.level(), holder, aura, amount, resource.value().particleColor()));
        return true;
    }

    private static int asWholeAmount(double value) {
        if (!Double.isFinite(value) || value < 1.0D) return 0;
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(value));
    }
}
