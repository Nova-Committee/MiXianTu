package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.SpiritData;
import com.iafenvoy.mxt.data.cultivation.CultivateAction;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;

/**
 * Drives the one persisted cultivation action using the entity's current chunk aura.
 */
public final class CultivationActionEventBridge {
    private CultivationActionEventBridge() {
    }

    public static void onEntityTick(Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide()) return;
        SpiritData spirit = entity.getData(MxtAttachments.SPIRIT_DATA);
        Holder<CultivateAction> action = spirit.cultivateAction().orElse(null);
        if (action == null) return;
        Identifier actionId = action.unwrapKey().orElseThrow().identifier();
        CultivateAction definition = action.value();
        FormulaContext context = FormulaContexts.forEntity(entity);
        boolean mayContinue = !definition.stopCondition().test(entity, context);
        AuraResult aura = AuraService.getPositionAura(entity.level(), entity.blockPosition());
        CultivationActionService.tick(entity, spirit, entity.getData(MxtAttachments.RESOURCE_HOLDER), aura, action, definition,
                entity.level().getGameTime(), context, () -> mayContinue);
    }
}
