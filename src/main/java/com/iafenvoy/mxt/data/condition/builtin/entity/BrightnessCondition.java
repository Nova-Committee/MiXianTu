package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

public record BrightnessCondition(Comparison comparison) implements EntityCondition {
    public static final MapCodec<BrightnessCondition> CODEC = Comparison.CODEC.xmap(BrightnessCondition::new, BrightnessCondition::comparison);

    @Override
    public boolean test(Entity entity) {
        BlockPos pos = BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ());
        if (!entity.level().getChunkSource().hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())))
            return this.comparison.compare(0.0D);
        float raw = (float) entity.level().getMaxLocalRawBrightness(pos) / 15.0F;
        float adjusted = raw / (4.0F - 3.0F * raw);
        return this.comparison.compare(Mth.lerp(entity.level().dimensionType().ambientLight(), adjusted, 1.0F));
    }

    @Override
    public MapCodec<BrightnessCondition> codec() {
        return CODEC;
    }
}
