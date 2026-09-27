package com.iafenvoy.mxt.data.condition.builtin.block.meta;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.data.context.condition.BlockConditionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NonNull;

public record OffsetCondition(BlockCondition condition, int x, int y, int z) implements BlockCondition {
    public static final MapCodec<OffsetCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BlockCondition.CODEC.fieldOf("condition").forGetter(OffsetCondition::condition),
            Codec.INT.optionalFieldOf("x", 0).forGetter(OffsetCondition::x),
            Codec.INT.optionalFieldOf("y", 0).forGetter(OffsetCondition::y),
            Codec.INT.optionalFieldOf("z", 0).forGetter(OffsetCondition::z)
    ).apply(i, OffsetCondition::new));

    @Override
    public boolean test(@NonNull BlockConditionContext ctx) {
        return this.condition.test(ctx.level(), ctx.pos().offset(this.x, this.y, this.z), ctx);
    }

    @Override
    public @NonNull MapCodec<OffsetCondition> codec() {
        return CODEC;
    }
}
