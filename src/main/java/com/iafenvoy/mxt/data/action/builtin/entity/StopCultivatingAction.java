package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.data.cultivation.CultivateAction;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.cultivation.CultivationModeService;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

/**
 * Stops the method the entity is running, writing its cooldown the way the player's own key does. Server only.
 */
public record StopCultivatingAction() implements EntityAction {
    public static final MapCodec<StopCultivatingAction> CODEC = MapCodec.unit(new StopCultivatingAction());

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        if (!(ctx.entity() instanceof LivingEntity living) || living.level().isClientSide()) return;
        // Read first: an entity that never cultivated must not grow an empty attachment out of this.
        CultivationAttachment spirit = living.getExistingData(MxtAttachments.CULTIVATION).orElse(null);
        if (spirit == null || !spirit.cultivating()) return;
        spirit.cultivateAction().ifPresent(running -> CultivationModeService.stop(living, spirit, running));
    }

    @Override
    public @NonNull MapCodec<StopCultivatingAction> codec() {
        return CODEC;
    }
}
