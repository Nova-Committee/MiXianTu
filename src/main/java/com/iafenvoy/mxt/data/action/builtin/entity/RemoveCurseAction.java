package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.event.CurseRemoveEvent.Reason;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.curse.CurseService;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;

/**
 * Removes one curse through the same event-aware transaction used by expiry.
 */
public record RemoveCurseAction(Holder<Curse> curse) implements EntityAction {
    public static final MapCodec<RemoveCurseAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Curse.CODEC.fieldOf("curse").forGetter(RemoveCurseAction::curse)
    ).apply(instance, RemoveCurseAction::new));

    @Override
    public void execute(Entity entity, FormulaContext context) {
        CurseService.remove(entity.getData(MxtAttachments.CURSE_HOLDER), this.curse, Reason.CLEANSED, entity.level().getGameTime());
    }

    @Override
    public MapCodec<RemoveCurseAction> codec() {
        return CODEC;
    }
}
