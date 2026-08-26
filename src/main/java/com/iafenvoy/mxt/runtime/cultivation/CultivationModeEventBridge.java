package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.SpiritAttachment;
import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Applies the physical restrictions while a player is cultivating. */
@EventBusSubscriber
public final class CultivationModeEventBridge {
    private static final float CULTIVATION_HEIGHT_OFFSET = 10.0F / 16.0F;

    private CultivationModeEventBridge() {
    }

    @SubscribeEvent
    public static void onEntitySize(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Player player)
                || !player.getExistingData(MxtAttachments.SPIRIT_DATA).map(SpiritAttachment::cultivating).orElse(false)) return;
        EntityDimensions size = event.getNewSize();
        float height = Math.max(0.1F, size.height() - CULTIVATION_HEIGHT_OFFSET);
        float eyeHeight = Math.clamp(size.eyeHeight() - CULTIVATION_HEIGHT_OFFSET, 0.0F, height);
        event.setNewSize(new EntityDimensions(size.width(), height, eyeHeight, size.attachments(), size.fixed()));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()
                || !player.getExistingData(MxtAttachments.SPIRIT_DATA).map(SpiritAttachment::cultivating).orElse(false)) return;
        CultivationMovementService.reconcile(player);
        if (!CultivationMovementService.isMovementAllowed(player)) {
            Vec3 velocity = player.getDeltaMovement();
            player.setDeltaMovement(0.0D, velocity.y(), 0.0D);
        }
    }
}
