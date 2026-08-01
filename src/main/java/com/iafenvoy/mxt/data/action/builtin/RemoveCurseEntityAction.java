package com.iafenvoy.mxt.data.action.builtin;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.event.CurseRemoveEvent;
import com.iafenvoy.mxt.event.CurseRemoveEvent.Reason;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.curse.CurseService;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * Removes one curse through the same event-aware transaction used by expiry.
 */
public record RemoveCurseEntityAction(Identifier curse) implements EntityAction {
    public static final MapCodec<RemoveCurseEntityAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("curse").forGetter(RemoveCurseEntityAction::curse)
    ).apply(instance, RemoveCurseEntityAction::new));

    @Override
    public void execute(Entity entity) {
        CurseService.remove(entity.getData(MxtAttachments.CURSE_HOLDER), this.curse,
                Reason.CLEANSED, entity.level().getGameTime());
    }

    @Override
    public MapCodec<RemoveCurseEntityAction> codec() {
        return CODEC;
    }
}
