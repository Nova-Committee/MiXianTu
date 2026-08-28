package com.iafenvoy.mxt.data.action.builtin.entity.meta;

import com.iafenvoy.mxt.data.context.action.EntityActionContext;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public enum NoOpAction implements EntityAction {
    INSTANCE;
    public static final MapCodec<NoOpAction> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public void execute(EntityActionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
    }

    @Override
    public MapCodec<NoOpAction> codec() {
        return CODEC;
    }
}
