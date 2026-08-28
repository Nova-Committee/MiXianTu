package com.iafenvoy.mxt.runtime.tribulation;

import com.iafenvoy.mxt.attachment.TribulationAttachment;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtCriteriaTriggers;
import com.iafenvoy.mxt.runtime.tribulation.TribulationService.State;
import com.iafenvoy.mxt.runtime.tribulation.TribulationService.TickResult;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Advances only the active persisted tribulation for each server-side living entity.
 */
@EventBusSubscriber
public final class TribulationEventBridge {
    private TribulationEventBridge() {
    }

    @SubscribeEvent
    public static void onEntityTick(Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide()) return;
        TribulationAttachment data = entity.getData(MxtAttachments.TRIBULATION);
        data.tribulation().ifPresent(holder -> {
            TickResult result = TribulationService.tick(entity, data, holder.value(), entity.level().getGameTime(), FormulaContexts.forEntity(entity));
            if (result.state() == State.COMPLETED && entity instanceof ServerPlayer player) {
                MxtCriteriaTriggers.TRIBULATION.get().trigger(player, HolderHelper.id(holder));
            }
        });
    }
}
