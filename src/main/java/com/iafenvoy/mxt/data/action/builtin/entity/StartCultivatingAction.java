package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.data.cultivation.CultivateAction;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.cultivation.CultivationModeService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Starts a cultivation method: the one named, or the selector's pick. Server only, and silent when nothing can be
 * started - an action has no result to hand back, so the method just does not begin.
 */
public record StartCultivatingAction(Optional<Holder<CultivateAction>> action) implements EntityAction {
    public static final MapCodec<StartCultivatingAction> CODEC = CultivateAction.CODEC.optionalFieldOf("action")
            .xmap(StartCultivatingAction::new, StartCultivatingAction::action);

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        if (!(ctx.entity() instanceof LivingEntity living) || living.level().isClientSide()) return;
        // Read first, so a body that cannot start anything never grows an empty attachment out of this.
        CultivationAttachment running = living.getExistingData(MxtAttachments.CULTIVATION).orElse(null);
        if (running != null && running.cultivating()) return;
        FormulaContext context = FormulaContexts.forEntity(living, ctx.formula());
        Holder<CultivateAction> chosen = this.action.orElse(null);
        if (chosen == null) chosen = CultivationModeService.select(living, context).orElse(null);
        if (chosen == null) return;
        CultivationModeService.start(living, living.getData(MxtAttachments.CULTIVATION), chosen, context);
    }

    @Override
    public @NonNull MapCodec<StartCultivatingAction> codec() {
        return CODEC;
    }
}
