package com.iafenvoy.mxt.runtime.spirit;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.SpiritBurstCooldownAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.resource.ResourceUseService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;

import java.util.*;

/**
 * Server-side hold-to-fire controller for aura-defined spirit bursts.
 */
@EventBusSubscriber
public final class SpiritBurstService {
    public static final long FIRE_INTERVAL_TICKS = 10L;
    /**
     * What each player is holding the shortcut down for. The entries are auras rather than their ids: what is
     * being fired is an aura, and the id only arrives that way because a key binding travels as a name.
     */
    private static final Map<UUID, Set<Holder<Aura>>> ACTIVE_AURAS = new HashMap<>();

    private SpiritBurstService() {
    }

    public static void setFiring(ServerPlayer player, Optional<Identifier> auraId, boolean firing) {
        UUID playerId = player.getUUID();
        if (auraId.isEmpty()) {
            if (!firing) ACTIVE_AURAS.remove(playerId);
            return;
        }
        Optional<Holder<Aura>> valid = auraId.flatMap(id -> MxtDatapackRegistries.holder(MxtResourceKeys.AURA, id))
                .map(aura -> (Holder<Aura>) aura)
                .filter(aura -> aura.value().auraType().isPresent() && ResourceUseService.canUse(player, aura));
        if (valid.isEmpty()) return;
        Set<Holder<Aura>> active = ACTIVE_AURAS.computeIfAbsent(playerId, ignored -> new HashSet<>());
        if (firing) {
            boolean added = active.add(valid.get());
            if (added) fire(player, valid.get());
        } else {
            active.remove(valid.get());
            if (active.isEmpty()) ACTIVE_AURAS.remove(playerId);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Set<Holder<Aura>> active = ACTIVE_AURAS.get(player.getUUID());
        SpiritBurstCooldownAttachment cooldowns = player.getData(MxtAttachments.SPIRIT_BURST_COOLDOWNS);
        cooldowns.clearExpired(player.level().getGameTime());
        if (active != null && !active.isEmpty()) {
            for (Holder<Aura> aura : Set.copyOf(active)) fire(player, aura);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        ACTIVE_AURAS.remove(event.getEntity().getUUID());
    }

    private static void fire(ServerPlayer player, Holder<Aura> aura) {
        ResourceHolderAttachment holder = player.getData(MxtAttachments.RESOURCE_HOLDER);
        Set<Holder<Aura>> active = ACTIVE_AURAS.get(player.getUUID());
        if (active == null || !active.contains(aura)) return;
        active.removeIf(candidate -> candidate.value().auraType().isEmpty() || !ResourceUseService.canUse(player, candidate));
        SpiritBurstCooldownAttachment cooldowns = player.getData(MxtAttachments.SPIRIT_BURST_COOLDOWNS);
        if (cooldowns.isOnCooldown(aura, player.level().getGameTime())) return;
        if (tryFire(player, holder, aura)) {
            cooldowns.setCooldownUntil(aura, Math.addExact(player.level().getGameTime(), FIRE_INTERVAL_TICKS));
        }
    }

    /**
     * A positive {@code burst_amount} marks an aura that can be fired by the shortcut.
     */
    private static boolean tryFire(ServerPlayer player, ResourceHolderAttachment holder, Holder<Aura> aura) {
        Aura definition = aura.value();
        if (definition.auraType().isEmpty() || !ResourceUseService.canUse(player, aura)) return false;
        Holder<Resource> resource = definition.resource();
        FormulaContext context = ResourceService.formulaContext(player, resource, FormulaContext.of(player));
        int amount = asWholeAmount(definition.burstAmount().evaluate(context));
        if (amount <= 0) return false;
        if (!ResourceService.initialize(holder, resource, context).valid() || holder.get(resource) < amount)
            return false;
        if (!ResourceService.change(holder, resource, -amount, context).valid()) return false;
        player.level().addFreshEntity(new SpiritBurstEntity(player.level(), player, aura, amount, resource.value().particleColor()));
        return true;
    }

    private static int asWholeAmount(double value) {
        if (!Double.isFinite(value) || value < 1.0D) return 0;
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(value));
    }
}
