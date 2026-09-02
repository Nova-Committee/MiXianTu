package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

public record GainAirAction(int value) implements EntityAction {
    public static final MapCodec<GainAirAction> CODEC = Codec.INT.fieldOf("value").xmap(GainAirAction::new, GainAirAction::value);

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        entity.setAirSupply(entity.getAirSupply() + this.value);
    }

    @Override
    public @NonNull MapCodec<GainAirAction> codec() {
        return CODEC;
    }
}
