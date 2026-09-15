package com.iafenvoy.mxt.runtime.formation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem;
import net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Enforces formation wards at the events the world actually goes through.
 *
 * <p>Written the same way as the sect territory bridge next door, and for the same reason: a rule that
 * lives on the event boundary cannot be bypassed by whichever code path a caller happened to take. The
 * two are independent — each cancels on its own, so a position covered by both is refused by both, and
 * neither needs to know the other exists.</p>
 *
 * <p>Each handler passes the two ends of its action and lets {@link FormationProtection} decide which of
 * them matters. Handlers therefore carry no position rule of their own: the actor's position is resolved
 * from the actor inside the decision, the target's is handed over, and an action with no target passes
 * null.</p>
 *
 * <p>Explosions and mob griefing have no acting player, so they are checked with no actor at all: an
 * action with nobody behind it cannot be excused by who is standing there.</p>
 */
@EventBusSubscriber
public final class FormationProtectionBridge {
    private FormationProtectionBridge() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BreakBlockEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level
                && FormationProtection.prevented(level, FormationProtection.Action.BREAK, event.getPos(), player.getUUID()))
            event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level
                && FormationProtection.prevented(level, FormationProtection.Action.PLACE, event.getPos(), player.getUUID()))
            event.setCanceled(true);
    }

    /**
     * A right click on a block. Cancellations report {@code FAIL} rather than leaving the result to the
     * caller, because the client has already predicted the interaction locally and would otherwise swing at
     * a block that refuses to open.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUse(RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level
                && FormationProtection.prevented(level, FormationProtection.Action.INTERACT, event.getPos(), player.getUUID())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level
                && FormationProtection.prevented(level, FormationProtection.Action.ENTITY_INTERACT,
                event.getTarget().blockPosition(), player.getUUID())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /**
     * Armour stands and anything whose interaction position matters arrive here instead of at
     * {@link EntityInteract}, so the same rule has to be enforced on both.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteractSpecific(EntityInteractSpecific event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level
                && FormationProtection.prevented(level, FormationProtection.Action.ENTITY_INTERACT,
                event.getTarget().blockPosition(), player.getUUID())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /**
     * Using an item with nothing in front of the player: a bucket, a potion, a bow being drawn.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onItemUse(RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level
                && FormationProtection.prevented(level, FormationProtection.Action.ITEM_USE, null, player.getUUID())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /**
     * A melee swing at a living entity or an armour stand. A projectile is not covered, and the module's
     * own documentation says so rather than leaving it to be discovered.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getEntity().level() instanceof ServerLevel level
                && FormationProtection.prevented(level, FormationProtection.Action.ATTACK_ENTITY,
                event.getTarget().blockPosition(), player.getUUID()))
            event.setCanceled(true);
    }

    /**
     * Strips the protected positions out of an explosion, rather than cancelling the explosion.
     *
     * <p>Cancelling would spare the entities too, and an explosion inside a ward should still hurt what is
     * standing there; only the ground is the ward's business. The list is edited in place because that is
     * the list the explosion is about to iterate.</p>
     */
    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getExplosion().level() instanceof ServerLevel level)) return;
        List<BlockPos> affected = event.getAffectedBlocks();
        if (affected.isEmpty()) return;
        List<BlockPos> kept = new ArrayList<>(affected.size());
        boolean removed = false;
        for (BlockPos pos : affected) {
            if (FormationProtection.prevented(level, FormationProtection.Action.EXPLOSION, pos, null)) removed = true;
            else kept.add(pos);
        }
        if (!removed) return;
        affected.clear();
        affected.addAll(kept);
    }

    @SubscribeEvent
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (FormationProtection.prevented(level, FormationProtection.Action.MOB_GRIEFING,
                event.getEntity().blockPosition(), null))
            event.setCanGrief(false);
    }
}
