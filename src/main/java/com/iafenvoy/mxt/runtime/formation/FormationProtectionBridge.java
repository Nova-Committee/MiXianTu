package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.runtime.formation.FormationProtection.Action;
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
import net.neoforged.neoforge.event.level.ExplosionEvent.Detonate;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Enforces formation wards at the events the world actually goes through, so a rule on the event boundary
 * cannot be bypassed by whichever code path a caller took. Handlers pass the two ends of their action and
 * let {@link FormationProtection} decide which matters, so they carry no position rule of their own.
 */
@EventBusSubscriber
public final class FormationProtectionBridge {
    private FormationProtectionBridge() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BreakBlockEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level
                && FormationProtection.prevented(level, Action.BREAK, event.getPos(), player.getUUID()))
            event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level
                && FormationProtection.prevented(level, Action.PLACE, event.getPos(), player.getUUID()))
            event.setCanceled(true);
    }

    /**
     * A right click on a block. Cancellations report {@code FAIL}, because the client has already predicted
     * the interaction locally and would otherwise swing at a block that refuses to open.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUse(RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level
                && FormationProtection.prevented(level, Action.INTERACT, event.getPos(), player.getUUID())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level
                && FormationProtection.prevented(level, Action.ENTITY_INTERACT,
                event.getTarget().blockPosition(), player.getUUID())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /**
     * Armour stands and anything whose interaction position matters arrive here.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteractSpecific(EntityInteractSpecific event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level
                && FormationProtection.prevented(level, Action.ENTITY_INTERACT,
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
                && FormationProtection.prevented(level, Action.ITEM_USE, null, player.getUUID())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /**
     * A melee swing at a living entity or an armour stand. A projectile is not covered.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getEntity().level() instanceof ServerLevel level
                && FormationProtection.prevented(level, Action.ATTACK_ENTITY,
                event.getTarget().blockPosition(), player.getUUID()))
            event.setCanceled(true);
    }

    /**
     * Strips the protected positions out of an explosion, rather than cancelling it: cancelling would spare
     * the entities too, and only the ground is the ward's business. The list is edited in place because that
     * is the list the explosion is about to iterate.
     */
    @SubscribeEvent
    public static void onDetonate(Detonate event) {
        List<BlockPos> affected = event.getAffectedBlocks();
        if (affected.isEmpty()) return;
        List<BlockPos> kept = new ArrayList<>(affected.size());
        boolean removed = false;
        for (BlockPos pos : affected) {
            if (FormationProtection.prevented(event.getExplosion().level(), Action.EXPLOSION, pos, null)) removed = true;
            else kept.add(pos);
        }
        if (!removed) return;
        affected.clear();
        affected.addAll(kept);
    }

    @SubscribeEvent
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (FormationProtection.prevented(level, Action.MOB_GRIEFING,
                event.getEntity().blockPosition(), null))
            event.setCanGrief(false);
    }
}
