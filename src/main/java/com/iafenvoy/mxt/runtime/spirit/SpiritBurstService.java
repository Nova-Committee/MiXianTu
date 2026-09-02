package com.iafenvoy.mxt.runtime.spirit;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.SpiritBurstCooldownAttachment;
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
 * Server-side hold-to-fire controller for resource-defined spirit bursts.
 */
@EventBusSubscriber
public final class SpiritBurstService {
    public static final long FIRE_INTERVAL_TICKS = 10L;
    private static final Map<UUID, Set<Identifier>> ACTIVE_RESOURCES = new HashMap<>();

    private SpiritBurstService() {
    }

    public static void setFiring(ServerPlayer player, Optional<Identifier> resourceId, boolean firing) {
        UUID playerId = player.getUUID();
        if (resourceId.isEmpty()) {
            if (!firing) ACTIVE_RESOURCES.remove(playerId);
            return;
        }
        Optional<Identifier> valid = resourceId.filter(id -> MxtDatapackRegistries
                .holder(MxtResourceKeys.RESOURCE, id)
                .map(resource -> resource.value().auraType().isPresent() && ResourceUseService.canUse(player, resource)).orElse(false));
        if (valid.isEmpty()) return;
        Set<Identifier> active = ACTIVE_RESOURCES.computeIfAbsent(playerId, ignored -> new HashSet<>());
        if (firing) {
            boolean added = active.add(valid.get());
            if (added) fire(player, valid.get());
        } else {
            active.remove(valid.get());
            if (active.isEmpty()) ACTIVE_RESOURCES.remove(playerId);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Set<Identifier> active = ACTIVE_RESOURCES.get(player.getUUID());
        SpiritBurstCooldownAttachment cooldowns = player.getData(MxtAttachments.SPIRIT_BURST_COOLDOWNS);
        cooldowns.clearExpired(player.level().getGameTime());
        if (active != null && !active.isEmpty()) {
            for (Identifier resource : Set.copyOf(active)) fire(player, resource);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        ACTIVE_RESOURCES.remove(event.getEntity().getUUID());
    }

    private static void fire(ServerPlayer player, Identifier resourceId) {
        ResourceHolderAttachment holder = player.getData(MxtAttachments.RESOURCE_HOLDER);
        Set<Identifier> active = ACTIVE_RESOURCES.get(player.getUUID());
        if (active == null || !active.contains(resourceId)) return;
        active.removeIf(id -> MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, id)
                .map(resource -> resource.value().auraType().isEmpty() || !ResourceUseService.canUse(player, resource)).orElse(true));
        Holder<Resource> resource = MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, resourceId).orElse(null);
        if (resource == null) return;
        SpiritBurstCooldownAttachment cooldowns = player.getData(MxtAttachments.SPIRIT_BURST_COOLDOWNS);
        if (cooldowns.isOnCooldown(resource, player.level().getGameTime())) return;
        if (tryFire(player, holder, resource)) {
            cooldowns.setCooldownUntil(resource, Math.addExact(player.level().getGameTime(), FIRE_INTERVAL_TICKS));
        }
    }

    /**
     * A positive {@code burst_amount} marks a resource that can be fired by the shortcut.
     */
    private static boolean tryFire(ServerPlayer player, ResourceHolderAttachment holder, Holder<Resource> resource) {
        Resource definition = resource.value();
        if (definition.auraType().isEmpty() || !ResourceUseService.canUse(player, resource)) return false;
        FormulaContext context = ResourceService.formulaContext(player, resource, FormulaContext.of(player));
        int amount = asWholeAmount(definition.burstAmount().evaluate(context));
        if (amount <= 0) return false;
        if (!ResourceService.initialize(holder, resource, context).valid() || holder.get(resource) < amount)
            return false;
        if (!ResourceService.change(holder, resource, -amount, context).valid()) return false;
        player.level().addFreshEntity(new SpiritBurstEntity(player.level(), player, resource, amount, definition.particleColor()));
        return true;
    }

    private static int asWholeAmount(double value) {
        if (!Double.isFinite(value) || value < 1.0D) return 0;
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(value));
    }
}
