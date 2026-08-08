package com.iafenvoy.mxt.runtime.tribulation;

import com.iafenvoy.mxt.attachment.TribulationData;
import com.iafenvoy.mxt.data.Tribulation;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtCriteriaTriggers;
import com.iafenvoy.mxt.runtime.tribulation.TribulationService.State;
import com.iafenvoy.mxt.runtime.tribulation.TribulationService.TickResult;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import net.minecraft.core.Holder;
import com.iafenvoy.mxt.util.HolderHelper;
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
        data.tribulation().map(holder -> new Entry(holder, holder.value())).ifPresent(entry -> {
            TickResult result = TribulationService.tick(entity, data, entry.definition(), entity.level().getGameTime(), FormulaContexts.forEntity(entity));
            if (result.state() == State.COMPLETED && entity instanceof ServerPlayer player) {
                MxtCriteriaTriggers.TRIBULATION.get().trigger(player, HolderHelper.id(entry.holder()));
            }
        });
    }

    private record Entry(Holder<Tribulation> holder,
                         Tribulation definition) {
    }
}
