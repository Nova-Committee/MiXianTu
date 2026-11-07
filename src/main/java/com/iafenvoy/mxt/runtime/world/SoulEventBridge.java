package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.config.MxtServerConfig;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;

@EventBusSubscriber
public final class SoulEventBridge {
    private SoulEventBridge() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!event.isCanceled() && MxtServerConfig.INSTANCE.souls.spawnOnDeath.getValue())
            SoulService.transfer(event.getEntity(), "death");
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity) SoulService.clearExpiredRecord(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerLoggedInEvent event) {
        SoulService.clearExpiredRecord(event.getEntity());
    }
}
