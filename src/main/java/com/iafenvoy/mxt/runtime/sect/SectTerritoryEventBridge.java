package com.iafenvoy.mxt.runtime.sect;

import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.SectComponent;
import com.iafenvoy.mxt.attachment.SectTerritoryComponent;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Enforces a data-driven sect territory policy at the authoritative block event boundary.
 */
@EventBusSubscriber
public final class SectTerritoryEventBridge {
    public static final Identifier BREAK = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "territory_break");
    public static final Identifier PLACE = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "territory_place");
    public static final Identifier USE = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "territory_use");
    public static final Identifier CLAIM = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "territory_claim");

    private SectTerritoryEventBridge() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BreakBlockEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level && !permitted(player, level, event.getPos(), BREAK))
            event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level && !permitted(player, level, event.getPos(), PLACE))
            event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUse(RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level && !permitted(player, level, event.getPos(), USE))
            event.setCanceled(true);
    }

    /**
     * An unclaimed chunk is public; a claimed chunk delegates the decision to the owner's rank policy.
     */
    public static boolean permitted(ServerPlayer player, ServerLevel level, BlockPos pos, Identifier permission) {
        SectTerritoryComponent territory = level.getChunkAt(pos).getData(MxtAttachments.SECT_TERRITORY);
        Identifier owner = territory.owner().map(HolderHelper::id).orElse(null);
        if (owner == null) return true;
        SectComponent membership = player.getData(MxtAttachments.SECT);
        return MxtDatapackRegistries.holder(MxtResourceKeys.SECT, owner)
                .map(sect -> SectService.canUseTerritory(membership, sect, territory, permission))
                .orElse(false);
    }
}
