package com.iafenvoy.mxt.runtime.spirit;

import com.iafenvoy.mxt.attachment.ResourceHolderComponent;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side hold-to-fire controller for resource-defined spirit bursts.
 */
@EventBusSubscriber
public final class SpiritBurstService {
    private static final long FIRE_INTERVAL_TICKS = 10L;
    private static final Map<UUID, Long> NEXT_FIRE_TICK = new HashMap<>();
    private static final Map<UUID, Set<Identifier>> ACTIVE_RESOURCES = new HashMap<>();

    private SpiritBurstService() {
    }

    public static void setFiring(ServerPlayer player, Optional<Identifier> resourceId, boolean firing) {
        UUID playerId = player.getUUID();
        if (resourceId.isEmpty()) {
            if (!firing) {
                ACTIVE_RESOURCES.remove(playerId);
                NEXT_FIRE_TICK.remove(playerId);
            }
            return;
        }
        Optional<Identifier> valid = resourceId.filter(id -> MxtDatapackRegistries
                .holder(MxtResourceKeys.RESOURCE, id).map(resource -> resource.value().auraType().isPresent()).orElse(false));
        if (valid.isEmpty()) return;
        Set<Identifier> active = ACTIVE_RESOURCES.computeIfAbsent(playerId, ignored -> new HashSet<>());
        if (firing) {
            boolean added = active.add(valid.get());
            if (added && !NEXT_FIRE_TICK.containsKey(playerId)) {
                fire(player);
                NEXT_FIRE_TICK.put(playerId, player.level().getGameTime() + FIRE_INTERVAL_TICKS);
            }
        } else {
            active.remove(valid.get());
            if (active.isEmpty()) NEXT_FIRE_TICK.remove(playerId);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Long nextFireTick = NEXT_FIRE_TICK.get(player.getUUID());
        if (nextFireTick == null || player.level().getGameTime() < nextFireTick) return;
        fire(player);
        if (ACTIVE_RESOURCES.getOrDefault(player.getUUID(), Set.of()).isEmpty())
            NEXT_FIRE_TICK.remove(player.getUUID());
        else NEXT_FIRE_TICK.put(player.getUUID(), player.level().getGameTime() + FIRE_INTERVAL_TICKS);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        NEXT_FIRE_TICK.remove(event.getEntity().getUUID());
        ACTIVE_RESOURCES.remove(event.getEntity().getUUID());
    }

    private static void fire(ServerPlayer player) {
        ResourceHolderComponent holder = player.getData(MxtAttachments.RESOURCE_HOLDER);
        Set<Identifier> active = ACTIVE_RESOURCES.get(player.getUUID());
        if (active == null || active.isEmpty()) return;
        active.removeIf(id -> MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, id)
                .map(resource -> resource.value().auraType().isEmpty()).orElse(true));
        active.stream()
                .map(id -> MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, id).orElse(null))
                .filter(Objects::nonNull)
                .forEach(resource -> tryFire(player, holder, resource));
    }

    /**
     * A positive {@code burst_amount} marks a resource that can be fired by the shortcut.
     */
    private static boolean tryFire(ServerPlayer player, ResourceHolderComponent holder, Holder<Resource> resource) {
        Resource definition = resource.value();
        if (definition.auraType().isEmpty()) return false;
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
