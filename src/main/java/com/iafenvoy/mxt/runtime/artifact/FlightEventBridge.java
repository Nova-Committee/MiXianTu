package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.attachment.FlightData;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.artifact.FlightService.Failure;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;

public final class FlightEventBridge {
    private FlightEventBridge() {
    }

    public static void onEntityTick(Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) return;
        FlightData data = player.getData(MxtAttachments.FLIGHT);
        if (!data.active()) return;
        if (data.archetype().flatMap(id -> MxtDatapackRegistries.get(MxtDatapackRegistries.ITEM_ARCHETYPE, id)).isEmpty()) {
            FlightService.dismount(player, Failure.NOT_FLYABLE);
            return;
        }
        if (!FlightService.ownsEquippedArchetype(player, player.getMainHandItem(), data.archetype().orElseThrow())) {
            FlightService.dismount(player, Failure.NOT_OWNED);
            return;
        }
        FlightService.tick(player, data.archetype().flatMap(id -> MxtDatapackRegistries.get(MxtDatapackRegistries.ITEM_ARCHETYPE, id)).orElseThrow(), FormulaContext.of(player));
    }
}
