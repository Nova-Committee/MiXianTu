package com.iafenvoy.mxt.data.action.builtin;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public enum BiEntityNoOpAction implements BiEntityAction {
    INSTANCE;
    public static final MapCodec<BiEntityNoOpAction> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public void execute(Entity actor, Entity target, FormulaContext context) {
    }

    @Override
    public MapCodec<BiEntityNoOpAction> codec() {
        return CODEC;
    }
}
