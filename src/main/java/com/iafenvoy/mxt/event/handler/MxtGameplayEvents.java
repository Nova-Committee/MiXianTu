package com.iafenvoy.mxt.event.handler;

import com.iafenvoy.mxt.runtime.ability.AbilityEventBridge;
import com.iafenvoy.mxt.runtime.artifact.FlightEventBridge;
import com.iafenvoy.mxt.runtime.creature.ContractEventBridge;
import com.iafenvoy.mxt.runtime.creature.CreatureProfileEventBridge;
import com.iafenvoy.mxt.runtime.cultivation.CultivationActionEventBridge;
import com.iafenvoy.mxt.runtime.cultivation.LifeSpanEventBridge;
import com.iafenvoy.mxt.runtime.curse.CurseEventBridge;
import com.iafenvoy.mxt.runtime.forging.ForgingEventBridge;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.runtime.formation.FormationWorldTicker;
import com.iafenvoy.mxt.runtime.sect.SectTerritoryEventBridge;
import com.iafenvoy.mxt.runtime.tribulation.TribulationEventBridge;
import com.iafenvoy.mxt.runtime.world.AuraChunkTicker;
import com.iafenvoy.mxt.runtime.world.AuraZoneEventBridge;
import com.iafenvoy.mxt.runtime.world.RealmInstanceTicker;
import com.iafenvoy.mxt.runtime.world.RealmTravelEventBridge;
import com.iafenvoy.mxt.runtime.world.SoulEventBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem;
import net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent;
import net.neoforged.neoforge.event.level.ChunkEvent.Load;
import net.neoforged.neoforge.event.level.ChunkEvent.Unload;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

/**
 * Game-bus forwarding for runtime domain systems.
 */
@EventBusSubscriber
public final class MxtGameplayEvents {
    private MxtGameplayEvents() {
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        AbilityEventBridge.onLivingDamage(event);
        ContractEventBridge.onLivingDamage(event);
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity entity) {
            ItemBindingService.refreshEquipped(entity);
            ItemBindingService.tickMainHandWeapon(entity);
        }
        AbilityEventBridge.onEntityTick(event);
        FlightEventBridge.onEntityTick(event);
        LifeSpanEventBridge.onEntityTick(event);
        ContractEventBridge.onEntityTick(event);
        TribulationEventBridge.onEntityTick(event);
        CultivationActionEventBridge.onEntityTick(event);
        AuraZoneEventBridge.onEntityTick(event);
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        CurseEventBridge.onEntityJoin(event);
        CreatureProfileEventBridge.onEntityJoin(event);
    }

    @SubscribeEvent
    public static void onLevelTick(Post event) {
        CurseEventBridge.onLevelTick(event);
        AuraChunkTicker.onLevelTick(event);
        AuraZoneEventBridge.onLevelTick(event);
        FormationWorldTicker.onLevelTick(event);
        RealmInstanceTicker.onLevelTick(event);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        AbilityEventBridge.onAttack(event);
        ItemBindingService.onMainHandWeaponAttack(event.getEntity(), event.getTarget());
    }

    @SubscribeEvent
    public static void onItemUse(RightClickItem event) {
        if (event.getHand() == InteractionHand.MAIN_HAND)
            ItemBindingService.onMainHandWeaponUse(event.getEntity());
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        AbilityEventBridge.onLivingDeath(event);
        ForgingEventBridge.onLivingDeath(event);
        ContractEventBridge.onLivingDeath(event);
        CreatureProfileEventBridge.onLivingDeath(event);
        SoulEventBridge.onDeath(event);
    }

    @SubscribeEvent
    public static void onUseFinish(Finish event) {
        AbilityEventBridge.onItemUseFinish(event);
        ItemBindingService.onUseFinish(event.getEntity(), event.getItem());
    }

    @SubscribeEvent
    public static void onBlockUse(RightClickBlock event) {
        AbilityEventBridge.onBlockUse(event);
        SectTerritoryEventBridge.onUse(event);
    }

    @SubscribeEvent
    public static void onBlockBreak(BreakBlockEvent event) {
        AbilityEventBridge.onBlockBreak(event);
        SectTerritoryEventBridge.onBreak(event);
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level)
            AuraChunkTicker.markDirty(level, event.getPos());
    }

    @SubscribeEvent
    public static void onPlace(EntityPlaceEvent event) {
        SectTerritoryEventBridge.onPlace(event);
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level)
            AuraChunkTicker.markDirty(level, event.getPos());
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        AbilityEventBridge.onEquipmentChange(event);
    }

    @SubscribeEvent
    public static void onChunkLoad(Load event) {
        AuraChunkTicker.onChunkLoad(event);
    }

    @SubscribeEvent
    public static void onChunkUnload(Unload event) {
        AuraChunkTicker.onChunkUnload(event);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerLoggedInEvent event) {
        RealmTravelEventBridge.onPlayerLogin(event);
    }
}
