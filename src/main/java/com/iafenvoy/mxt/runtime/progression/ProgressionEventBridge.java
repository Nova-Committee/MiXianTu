package com.iafenvoy.mxt.runtime.progression;

import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.runtime.ability.AbilityGrantService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent.ServerDataLoad;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;

/**
 * Re-checks the stored levels when the definitions may have moved: when a body joins the world, and once per
 * datapack load. Both are rare on purpose - a level that is no longer on its owner's chain is a data-pack change
 * symptom, so it is swept there instead of on every read.
 */
@EventBusSubscriber
public final class ProgressionEventBridge {
    @SubscribeEvent
    public static void onPlayerLogin(PlayerLoggedInEvent event) {
        prune(event.getEntity());
    }

    // The creature half of a login: a body entering the world is checked once. A body with no record leaves on
    // the first attachment lookup, which is what keeps a chunk's worth of mobs from costing anything here.
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getEntity() instanceof LivingEntity living) prune(living);
    }

    // LOWEST so ServerCache has already rebuilt: clearing a record changes where its owner stands, and the grant
    // rebuild that follows reads that index.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDatapackLoaded(ServerDataLoad event) {
        ServerCache.get().map(ServerCache::server).ifPresent(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) prune(player);
            // Creatures never log in, so the loaded ones are visited here; the walk only looks at bodies that
            // already carry a record.
            for (ServerLevel level : server.getAllLevels())
                for (Entity entity : level.getEntities().getAll())
                    if (entity instanceof LivingEntity living) prune(living);
        });
    }

    private static void prune(LivingEntity entity) {
        if (ProgressionService.pruneForeignLevels(entity) == 0) return;
        AbilityGrantService.recalculate(entity);
    }
}
