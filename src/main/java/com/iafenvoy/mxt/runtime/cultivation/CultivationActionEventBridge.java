package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.SpiritAttachment;
import com.iafenvoy.mxt.data.cultivation.CultivateAction;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

/**
 * Drives the one persisted cultivation action using the entity's current chunk aura.
 */
@EventBusSubscriber
public final class CultivationActionEventBridge {
    private CultivationActionEventBridge() {
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide() || entity instanceof ServerPlayer)
            return;
        tick(entity);
    }

    @SubscribeEvent
    public static void onLevelTick(Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        AuraDistributionService.prepare(level);
        for (ServerPlayer player : level.players()) tick(player);
    }

    private static void tick(LivingEntity entity) {
        SpiritAttachment spirit = entity.getData(MxtAttachments.SPIRIT_DATA);
        Holder<CultivateAction> action = spirit.cultivateAction().orElse(null);
        if (action == null) return;
        CultivateAction definition = action.value();
        FormulaContext context = FormulaContexts.forEntity(entity);
        boolean mayContinue = !definition.stopCondition().test(entity, context);
        AuraResult aura = AuraService.getPositionAura(entity.level(), entity.blockPosition());
        CultivationActionService.tick(entity, spirit, entity.getData(MxtAttachments.RESOURCE_HOLDER), aura, action, definition,
                entity.level().getGameTime(), context, () -> mayContinue);
        // Cultivation can change progress, active-state timing, and resource conversions in one tick.
    }
}
