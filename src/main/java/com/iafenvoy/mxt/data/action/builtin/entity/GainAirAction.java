package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.context.action.EntityActionContext;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public record GainAirAction(int value) implements EntityAction {
    public static final MapCodec<GainAirAction> CODEC = Codec.INT.fieldOf("value").xmap(GainAirAction::new, GainAirAction::value);

    @Override
    public void execute(EntityActionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        entity.setAirSupply(entity.getAirSupply() + this.value);
    }

    @Override
    public MapCodec<GainAirAction> codec() {
        return CODEC;
    }
}
