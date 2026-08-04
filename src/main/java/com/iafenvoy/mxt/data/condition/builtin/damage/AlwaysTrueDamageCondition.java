package com.iafenvoy.mxt.data.condition.builtin.damage;

import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.damagesource.DamageSource;

public enum AlwaysTrueDamageCondition implements DamageCondition {
    INSTANCE;
    public static final MapCodec<AlwaysTrueDamageCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(DamageSource source, float amount, FormulaContext context) {
        return true;
    }

    @Override
    public MapCodec<AlwaysTrueDamageCondition> codec() {
        return CODEC;
    }
}
