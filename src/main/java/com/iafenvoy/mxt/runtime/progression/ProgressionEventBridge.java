package com.iafenvoy.mxt.runtime.progression;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.runtime.cultivation.CultivationGrantService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent.ServerDataLoad;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;

/**
 * Re-checks the stored levels when the definitions may have moved: on login, and once per datapack load. Both are
 * rare on purpose - a level that is no longer on its owner's chain is a data-pack change symptom, so it is swept
 * there instead of on every read.
 */
@EventBusSubscriber
public final class ProgressionEventBridge {
    @SubscribeEvent
    public static void onPlayerLogin(PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) prune(player);
    }

    // LOWEST so ServerCache has already rebuilt: clearing a record changes where its owner stands, and the grant
    // rebuild that follows reads that index.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDatapackLoaded(ServerDataLoad event) {
        ServerCache.get().map(ServerCache::server).ifPresent(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) prune(player);
        });
    }

    private static void prune(LivingEntity entity) {
        if (ProgressionService.pruneForeignLevels(entity) == 0) return;
        SpiritIdentityAttachment spirit = entity.getExistingData(MxtAttachments.SPIRIT_IDENTITY).orElse(null);
        if (spirit == null) return;
        CultivationGrantService.recalculate(entity, spirit, entity.getData(MxtAttachments.ABILITY_HOLDER));
    }
}
