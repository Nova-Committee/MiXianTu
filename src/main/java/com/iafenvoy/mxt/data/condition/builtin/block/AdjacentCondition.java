package com.iafenvoy.mxt.data.condition.builtin.block;

import com.iafenvoy.mxt.data.context.condition.BlockConditionContext;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

public record AdjacentCondition(BlockCondition adjacentCondition, Comparison comparison) implements BlockCondition {
    public static final MapCodec<AdjacentCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BlockCondition.CODEC.fieldOf("adjacent_condition").forGetter(AdjacentCondition::adjacentCondition),
            Comparison.CODEC.forGetter(AdjacentCondition::comparison)
    ).apply(i, AdjacentCondition::new));

    @Override
    public boolean test(@NonNull BlockConditionContext ctx) {
        Level level = ctx.level();
        BlockPos pos = ctx.pos();
        FormulaContext context = ctx.formula();
        int matches = 0;
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = pos.relative(direction);
            if (level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(adjacent.getX()), SectionPos.blockToSectionCoord(adjacent.getZ())) && this.adjacentCondition.test(level, adjacent, ctx))
                matches++;
        }
        return this.comparison.compare(matches);
    }

    @Override
    public @NonNull MapCodec<AdjacentCondition> codec() {
        return CODEC;
    }
}
