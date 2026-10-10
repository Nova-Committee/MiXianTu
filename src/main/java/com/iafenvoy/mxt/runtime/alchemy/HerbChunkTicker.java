package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.attachment.HerbChunkAttachment;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.world.AuraChunkTicker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

/**
 * Drives the herb settlement over the loaded chunks, once per growth period, opens a plant's clock when its block
 * is placed, and stamps the age onto a broken plant's fruit. The framework owns all three, rather than each
 * carrying block: a block a pack claims needs no ticker, cannot pick a pace other than the one {@code growth_rate}
 * is written against, and gets its fruit stamped without writing any drop code.
 */
@EventBusSubscriber
public final class HerbChunkTicker {
    private HerbChunkTicker() {
    }

    @SubscribeEvent
    public static void onLevelTick(Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.getGameTime() % SpiritHerbGrowthService.PERIOD != 0L) return;
        for (LevelChunk chunk : AuraChunkTicker.loadedChunks(level)) {
            HerbChunkAttachment attachment = chunk.getExistingDataOrNull(MxtAttachments.HERB_CHUNK.get());
            if (attachment == null) continue;
            SpiritHerbGrowthService.settle(level, chunk, attachment);
        }
    }

    // Planting. A block a pack claims starts its clock the moment it lands, whether a player placed it or an
    // action did; a block the world generated never comes through here and is picked up as a wild plant instead.
    @SubscribeEvent
    public static void onBlockPlace(EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        SpiritHerbGrowthService.placed(level, event.getPos(), event.getPlacedBlock());
    }

    // The block is already gone or is about to be, so the age is read from the event's own state rather than asked
    // of the level - which is why this runs on the drops event and not on the break event.
    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        SpiritHerbGrowthService.harvested(event.getLevel(), event.getPos(), event.getState(), event.getDrops());
    }
}
