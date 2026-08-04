package com.iafenvoy.mxt.data.condition.builtin.bientity.meta;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public enum AlwaysTrueBiEntityCondition implements BiEntityCondition {
    INSTANCE;
    public static final MapCodec<AlwaysTrueBiEntityCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(Entity actor, Entity target, FormulaContext context) {
        return true;
    }

    @Override
    public MapCodec<AlwaysTrueBiEntityCondition> codec() {
        return CODEC;
    }
}
