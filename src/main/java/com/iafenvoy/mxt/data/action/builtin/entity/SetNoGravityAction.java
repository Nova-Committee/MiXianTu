package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.context.action.EntityActionContext;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import net.minecraft.world.entity.Entity;

public record SetNoGravityAction(boolean noGravity) implements EntityAction {
    public static final MapCodec<SetNoGravityAction> CODEC = Codec.BOOL.optionalFieldOf("no_gravity", true).xmap(SetNoGravityAction::new, SetNoGravityAction::noGravity);

    @Override
    public void execute(EntityActionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        entity.setNoGravity(this.noGravity);
    }

    @Override
    public MapCodec<SetNoGravityAction> codec() {
        return CODEC;
    }
}
