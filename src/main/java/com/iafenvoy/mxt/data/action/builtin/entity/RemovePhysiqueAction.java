package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

/**
 * Removes one held physique together with its granted abilities and attribute sources.
 */
public record RemovePhysiqueAction(Holder<Physique> physique) implements EntityAction {
    public static final MapCodec<RemovePhysiqueAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Physique.CODEC.fieldOf("physique").forGetter(RemovePhysiqueAction::physique)
    ).apply(i, RemovePhysiqueAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        if (entity instanceof LivingEntity living)
            CultivationIdentityService.removePhysique(living, HolderHelper.id(this.physique));
    }

    @Override
    public @NonNull MapCodec<RemovePhysiqueAction> codec() {
        return CODEC;
    }
}
