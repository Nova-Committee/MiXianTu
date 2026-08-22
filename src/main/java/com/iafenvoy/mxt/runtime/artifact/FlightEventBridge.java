package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.attachment.FlightComponent;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.artifact.FlightService.Failure;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber
public final class FlightEventBridge {
    private FlightEventBridge() {
    }

    @SubscribeEvent
    public static void onEntityTick(Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) return;
        FlightComponent data = player.getData(MxtAttachments.FLIGHT);
        if (!data.active()) return;
        if (data.archetype().isEmpty()) {
            FlightService.dismount(player, Failure.NOT_FLYABLE);
            return;
        }
        if (!FlightService.ownsEquippedArchetype(player, player.getMainHandItem(), data.archetype().orElseThrow())) {
            FlightService.dismount(player, Failure.NOT_OWNED);
            return;
        }
        FlightService.tick(player, data.archetype().orElseThrow().value(), FormulaContext.of(player));
    }
}
