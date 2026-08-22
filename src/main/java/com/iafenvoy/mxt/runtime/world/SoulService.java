package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.attachment.SoulComponent;
import com.iafenvoy.mxt.event.SoulEvent.ReclaimPost;
import com.iafenvoy.mxt.event.SoulEvent.ReclaimPre;
import com.iafenvoy.mxt.event.SoulEvent.TransferPost;
import com.iafenvoy.mxt.event.SoulEvent.TransferPre;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtEntityTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;

import java.util.UUID;

/**
 * Death-to-soul transition, manifestation and explicit recovery service.
 */
public final class SoulService {
    private SoulService() {
    }

    public static boolean transfer(Entity entity, String source) {
        SoulComponent soul = entity.getData(MxtAttachments.SOUL);
        if (NeoForge.EVENT_BUS.post(new TransferPre(entity, soul)).isCanceled()) return false;
        SoulEntity manifestation = null;
        if (entity.level() instanceof ServerLevel level) {
            manifestation = new SoulEntity(MxtEntityTypes.SOUL.get(), level);
            manifestation.bind(entity.getUUID(), level.getGameTime(), source);
            manifestation.setPos(entity.getX(), entity.getY(), entity.getZ());
            if (!level.addFreshEntity(manifestation)) manifestation = null;
        }
        soul.activate(entity.getUUID(), entity.level().getGameTime(), source, manifestation == null ? null : manifestation.getUUID());
        NeoForge.EVENT_BUS.post(new TransferPost(entity, soul));
        return true;
    }

    /**
     * Clears only the caller's own active soul and removes its recorded manifestation when available.
     */
    public static boolean reclaim(Entity entity) {
        SoulComponent soul = entity.getData(MxtAttachments.SOUL);
        if (!soul.active() || !entity.getUUID().toString().equals(soul.origin())) return false;
        if (NeoForge.EVENT_BUS.post(new ReclaimPre(entity, soul)).isCanceled()) return false;
        if (entity.level() instanceof ServerLevel level) {
            UUID manifestation = soul.manifestation().orElse(null);
            if (manifestation != null) {
                for (ServerLevel world : level.getServer().getAllLevels()) {
                    Entity candidate = world.getEntity(manifestation);
                    if (candidate instanceof SoulEntity) {
                        candidate.discard();
                        break;
                    }
                }
            }
        }
        soul.clear();
        NeoForge.EVENT_BUS.post(new ReclaimPost(entity, soul));
        return true;
    }
}
