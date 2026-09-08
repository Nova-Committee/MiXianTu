package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

/**
 * Removes one held spirit root together with its granted abilities.
 */
public record RemoveSpiritRootAction(Holder<SpiritRoot> spiritRoot) implements EntityAction {
    public static final MapCodec<RemoveSpiritRootAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SpiritRoot.CODEC.fieldOf("spirit_root").forGetter(RemoveSpiritRootAction::spiritRoot)
    ).apply(i, RemoveSpiritRootAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        if (entity instanceof LivingEntity living)
            CultivationIdentityService.removeSpiritRoot(living, HolderHelper.id(this.spiritRoot));
    }

    @Override
    public @NonNull MapCodec<RemoveSpiritRootAction> codec() {
        return CODEC;
    }
}
