package com.iafenvoy.mxt.runtime.sect;

import com.iafenvoy.mxt.attachment.SectData;
import com.iafenvoy.mxt.attachment.SectTerritoryData;
import com.iafenvoy.mxt.data.sect.SectDefinition;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Enforces a data-driven sect territory policy at the authoritative block event boundary.
 */
public final class SectTerritoryEventBridge {
    public static final Identifier BREAK = Identifier.fromNamespaceAndPath("mxt", "territory_break");
    public static final Identifier PLACE = Identifier.fromNamespaceAndPath("mxt", "territory_place");
    public static final Identifier USE = Identifier.fromNamespaceAndPath("mxt", "territory_use");
    public static final Identifier CLAIM = Identifier.fromNamespaceAndPath("mxt", "territory_claim");

    private SectTerritoryEventBridge() {
    }

    public static void onBreak(BreakBlockEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level && !permitted(player, level, event.getPos(), BREAK))
            event.setCanceled(true);
    }

    public static void onPlace(EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level && !permitted(player, level, event.getPos(), PLACE))
            event.setCanceled(true);
    }

    public static void onUse(RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level && !permitted(player, level, event.getPos(), USE))
            event.setCanceled(true);
    }

    /**
     * An unclaimed chunk is public; a claimed chunk delegates the decision to the owner's rank policy.
     */
    public static boolean permitted(ServerPlayer player, ServerLevel level, BlockPos pos, Identifier permission) {
        SectTerritoryData territory = level.getChunkAt(pos).getData(MxtAttachments.SECT_TERRITORY);
        Identifier owner = territory.owner().orElse(null);
        if (owner == null) return true;
        SectData membership = player.getData(MxtAttachments.SECT);
        SectDefinition definition = MxtDatapackRegistries.get(MxtDatapackRegistries.SECT, owner).orElse(null);
        return definition != null && SectService.canUseTerritory(membership, owner, definition, territory, permission);
    }
}
