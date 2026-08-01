package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.AuraChunkData;
import com.iafenvoy.mxt.attachment.SpiritData;
import com.iafenvoy.mxt.data.cultivation.CultivateActionDefinition;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
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
        Identifier actionId = spirit.cultivateAction().orElse(null);
        if (actionId == null) return;
        CultivateActionDefinition definition = MxtDatapackRegistries.get(MxtDatapackRegistries.CULTIVATE_ACTION, actionId).orElse(null);
        if (definition == null) {
            spirit.stopCultivateAction(actionId, entity.level().getGameTime());
            return;
        }
        FormulaContext context = FormulaContexts.forEntity(entity);
        boolean mayContinue = definition.stopConditions().stream().noneMatch(id -> MxtTypeRegistries.CULTIVATION_CONDITION.get(id)
                .map(reference -> reference.value().test(entity, context)).orElse(true));
        AuraChunkData aura = entity.level().getChunkAt(entity.blockPosition()).getData(MxtAttachments.AURA_CHUNK);
        CultivationActionService.tick(entity, spirit, entity.getData(MxtAttachments.RESOURCE_HOLDER), aura, actionId, definition,
                entity.level().getGameTime(), context, () -> mayContinue);
    }
}
