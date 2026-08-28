package com.iafenvoy.mxt.data.action.builtin.bientity.meta;

import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

public enum BiEntityNoOpAction implements BiEntityAction {
    INSTANCE;
    public static final MapCodec<BiEntityNoOpAction> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public void execute(@NonNull BiEntityActionContext ctx) {
        Entity actor = ctx.actor();
        Entity target = ctx.target();
        FormulaContext context = ctx.formula();
    }

    @Override
    public @NonNull MapCodec<BiEntityNoOpAction> codec() {
        return CODEC;
    }
}
