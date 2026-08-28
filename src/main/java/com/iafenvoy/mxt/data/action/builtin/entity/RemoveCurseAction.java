package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.context.action.EntityActionContext;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.event.CurseRemoveEvent.Reason;
import com.iafenvoy.mxt.runtime.curse.CurseService;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

/**
 * Removes one curse through the same event-aware transaction used by expiry.
 */
public record RemoveCurseAction(Holder<Curse> curse) implements EntityAction {
    public static final MapCodec<RemoveCurseAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Curse.CODEC.fieldOf("curse").forGetter(RemoveCurseAction::curse)
    ).apply(i, RemoveCurseAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        CurseService.remove(entity, this.curse, Reason.CLEANSED, entity.level().getGameTime());
    }

    @Override
    public @NonNull MapCodec<RemoveCurseAction> codec() {
        return CODEC;
    }
}
