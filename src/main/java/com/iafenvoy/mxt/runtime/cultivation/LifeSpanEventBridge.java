package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.config.MxtServerConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;

@EventBusSubscriber
public final class LifeSpanEventBridge {
    private LifeSpanEventBridge() {
    }

    // Fires for every non-passenger entity of every level, so the switch and the interval are checked before the
    // service is asked anything; the modulo is global, which keeps every body on the same settlement beat
    // instead of storing a counter per entity.
    @SubscribeEvent
    public static void onEntityTick(Post event) {
        Entity entity = event.getEntity();
        Level level = entity.level();
        if (level.isClientSide()) return;
        MxtServerConfig.Lifespan settings = MxtServerConfig.INSTANCE.lifespan;
        if (!settings.enabled.getValue()) return;
        if (level.getGameTime() % settings.settleInterval.getValue() != 0L) return;
        LifeSpanService.settle(entity);
    }

    // Only the first settlement would seed otherwise, which leaves the panel empty until then.
    @SubscribeEvent
    public static void onPlayerLogin(PlayerLoggedInEvent event) {
        LifeSpanService.seed(event.getEntity());
    }
}
