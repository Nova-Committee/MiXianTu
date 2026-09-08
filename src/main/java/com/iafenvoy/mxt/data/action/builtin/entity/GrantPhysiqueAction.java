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
 * Grants one configured physique after its holder condition, stacking and exclusivity checks pass.
 */
public record GrantPhysiqueAction(Holder<Physique> physique) implements EntityAction {
    public static final MapCodec<GrantPhysiqueAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Physique.CODEC.fieldOf("physique").forGetter(GrantPhysiqueAction::physique)
    ).apply(i, GrantPhysiqueAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        if (entity instanceof LivingEntity living)
            CultivationIdentityService.grantPhysique(living, HolderHelper.id(this.physique), this.physique.value(), ctx.formula());
    }

    @Override
    public @NonNull MapCodec<GrantPhysiqueAction> codec() {
        return CODEC;
    }
}
