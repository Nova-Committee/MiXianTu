package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

public enum ExposedToSunCondition implements EntityCondition {
    INSTANCE;
    public static final MapCodec<ExposedToSunCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(Entity entity) {
        BlockPos pos = BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ());
        long time = entity.level().getOverworldClockTime() % 24_000L;
        return time < 12_000L && entity.level().canSeeSky(pos) && !entity.level().isRainingAt(pos) && entity.level().getMaxLocalRawBrightness(pos) > 7;
    }

    @Override
    public MapCodec<ExposedToSunCondition> codec() {
        return CODEC;
    }
}
