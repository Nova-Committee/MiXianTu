package com.iafenvoy.mxt.runtime.tribulation;

import com.iafenvoy.mxt.attachment.TribulationData;
import com.iafenvoy.mxt.data.tribulation.TribulationDefinition;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtCriteriaTriggers;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.tribulation.TribulationService.State;
import com.iafenvoy.mxt.runtime.tribulation.TribulationService.TickResult;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;

/**
 * Advances only the active persisted tribulation for each server-side living entity.
 */
public final class TribulationEventBridge {
    private TribulationEventBridge() {
    }

    public static void onEntityTick(Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide()) return;
        TribulationData data = entity.getData(MxtAttachments.TRIBULATION);
        data.tribulation().flatMap(id -> MxtDatapackRegistries.get(MxtDatapackRegistries.TRIBULATION, id).map(definition -> new Entry(id, definition))).ifPresent(entry -> {
            TickResult result = TribulationService.tick(data, entry.definition(), entity.level().getGameTime(), FormulaContexts.forEntity(entity));
            if (result.state() == State.COMPLETED && entity instanceof ServerPlayer player) {
                MxtCriteriaTriggers.TRIBULATION.get().trigger(player, entry.id());
            }
        });
    }

    private record Entry(Identifier id,
                         TribulationDefinition definition) {
    }
}
