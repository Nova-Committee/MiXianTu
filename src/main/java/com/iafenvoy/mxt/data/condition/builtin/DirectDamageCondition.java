package com.iafenvoy.mxt.data.condition.builtin;

import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Checks whether a damage source has a physical/direct entity independent of its owner.
 */
public record DirectDamageCondition(boolean direct) implements DamageCondition {
    public static final MapCodec<DirectDamageCondition> CODEC = Codec.BOOL.optionalFieldOf("direct", true).xmap(DirectDamageCondition::new, DirectDamageCondition::direct);

    @Override
    public boolean test(DamageSource source, float amount, FormulaContext context) {
        return (source.getDirectEntity() != null) == this.direct;
    }

    @Override
    public MapCodec<DirectDamageCondition> codec() {
        return CODEC;
    }
}
