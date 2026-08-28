package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.context.action.EntityActionContext;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import net.minecraft.world.entity.Entity;

public record SetOnFireAction(int ticks) implements EntityAction {
    public static final MapCodec<SetOnFireAction> CODEC = Codec.INT.fieldOf("ticks").xmap(SetOnFireAction::new, SetOnFireAction::ticks);

    @Override
    public void execute(EntityActionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        entity.setRemainingFireTicks(this.ticks);
    }

    @Override
    public MapCodec<SetOnFireAction> codec() {
        return CODEC;
    }
}
