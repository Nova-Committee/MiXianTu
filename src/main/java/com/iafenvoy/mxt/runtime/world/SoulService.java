package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.attachment.SoulAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.entity.SoulEntity;
import com.iafenvoy.mxt.event.SoulEvent.ReclaimPost;
import com.iafenvoy.mxt.event.SoulEvent.ReclaimPre;
import com.iafenvoy.mxt.event.SoulEvent.TransferPost;
import com.iafenvoy.mxt.event.SoulEvent.TransferPre;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtEntityTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Death-to-soul transition, manifestation and explicit recovery service.
 */
public final class SoulService {
    private static final ThreadLocal<Set<UUID>> IN_TRANSITION = ThreadLocal.withInitial(HashSet::new);

    private SoulService() {
    }

    public static boolean transfer(Entity entity, String source) {
        if (!(entity instanceof LivingEntity living) || !(entity.level() instanceof ServerLevel level) || entity.isRemoved())
            return false;
        Set<UUID> pending = IN_TRANSITION.get();
        if (!pending.add(entity.getUUID())) return false;
        try {
            clearExpiredRecord(entity);
            SoulAttachment soul = entity.getExistingData(MxtAttachments.SOUL).orElseGet(SoulAttachment::new);
            if (soul.active() || NeoForge.EVENT_BUS.post(new TransferPre(entity, soul)).isCanceled() || soul.active() || entity.isRemoved())
                return false;
            SoulEntity manifestation = new SoulEntity(MxtEntityTypes.SOUL.get(), level);
            long createdAt = level.getServer().overworld().getGameTime();
            manifestation.capture(living, createdAt, source);
            if (!level.addFreshEntity(manifestation) || manifestation.isRemoved()) return false;
            soul.activate(entity.getUUID(), createdAt, source, manifestation.getUUID());
            entity.setData(MxtAttachments.SOUL, soul);
            NeoForge.EVENT_BUS.post(new TransferPost(entity, soul));
            return true;
        } finally {
            pending.remove(entity.getUUID());
            if (pending.isEmpty()) IN_TRANSITION.remove();
        }
    }

    /**
     * Clears only the caller's own active soul and removes its recorded manifestation when available.
     */
    public static boolean reclaim(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return false;
        Set<UUID> pending = IN_TRANSITION.get();
        if (!pending.add(entity.getUUID())) return false;
        try {
            clearExpiredRecord(entity);
            SoulAttachment soul = entity.getExistingData(MxtAttachments.SOUL).orElse(null);
            if (soul == null || !soul.active() || !entity.getUUID().toString().equals(soul.origin())) return false;
            UUID manifestation = soul.manifestation().orElse(null);
            SoulEntity found = manifestation == null ? null : findManifestation(level, entity.getUUID(), manifestation).orElse(null);
            // A live but unloaded manifestation must retain its pointer until it returns or times out.
            if (manifestation != null && found == null) return false;
            if (NeoForge.EVENT_BUS.post(new ReclaimPre(entity, soul)).isCanceled()) return false;
            if (found != null) found.discard();
            soul.clear();
            NeoForge.EVENT_BUS.post(new ReclaimPost(entity, soul));
            return true;
        } finally {
            pending.remove(entity.getUUID());
            if (pending.isEmpty()) IN_TRANSITION.remove();
        }
    }

    public static boolean expired(ServerLevel level, long createdAt) {
        int lifetime = MxtServerConfig.INSTANCE.souls.lifetimeTicks.getValue();
        return lifetime > 0 && createdAt >= 0 && level.getServer().overworld().getGameTime() - createdAt >= lifetime;
    }

    // An unloaded source cannot be edited at expiry; its timestamp invalidates the pointer when it returns.
    public static void clearExpiredRecord(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        SoulAttachment soul = entity.getExistingData(MxtAttachments.SOUL).orElse(null);
        if (soul == null || !soul.active() || !expired(level, soul.createdAt())) return;
        soul.manifestation().flatMap(id -> findManifestation(level, entity.getUUID(), id)).ifPresent(Entity::discard);
        soul.clear();
    }

    public static void expire(SoulEntity manifestation) {
        if (!(manifestation.level() instanceof ServerLevel level)) return;
        manifestation.origin().ifPresent(id -> {
            for (ServerLevel world : level.getServer().getAllLevels()) {
                Entity origin = world.getEntity(id);
                if (origin == null) continue;
                origin.getExistingData(MxtAttachments.SOUL)
                        .filter(soul -> soul.manifestation().filter(manifestation.getUUID()::equals).isPresent())
                        .ifPresent(SoulAttachment::clear);
                break;
            }
        });
        manifestation.discard();
    }

    private static Optional<SoulEntity> findManifestation(ServerLevel level, UUID origin, UUID manifestation) {
        for (ServerLevel world : level.getServer().getAllLevels()) {
            Entity candidate = world.getEntity(manifestation);
            if (candidate instanceof SoulEntity ghost && ghost.origin().filter(origin::equals).isPresent())
                return Optional.of(ghost);
        }
        return Optional.empty();
    }
}
