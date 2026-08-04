package com.iafenvoy.mxt.data.condition.builtin.block;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public record AuraRangeBlockCondition(NumberProvider min, NumberProvider max) implements BlockCondition {
    public static final MapCodec<AuraRangeBlockCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            NumberProvider.CODEC.fieldOf("min").forGetter(AuraRangeBlockCondition::min),
            NumberProvider.CODEC.fieldOf("max").forGetter(AuraRangeBlockCondition::max)
    ).apply(instance, AuraRangeBlockCondition::new));

    @Override
    public boolean test(Level level, BlockPos pos, FormulaContext context) {
        double min = this.min.evaluate(context);
        double max = this.max.evaluate(context);
        if (!Double.isFinite(min) || !Double.isFinite(max) || min > max) return false;
        double aura = AuraService.getPositionAura(level, pos).concentration();
        return aura >= min && aura <= max;
    }

    @Override
    public MapCodec<AuraRangeBlockCondition> codec() {
        return CODEC;
    }
}
