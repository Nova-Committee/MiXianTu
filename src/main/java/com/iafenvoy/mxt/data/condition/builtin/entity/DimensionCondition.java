package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public record DimensionCondition(ResourceKey<Level> dimension, boolean inverted) implements EntityCondition {
    public static final MapCodec<DimensionCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(DimensionCondition::dimension),
            Codec.BOOL.optionalFieldOf("inverted", false).forGetter(DimensionCondition::inverted)
    ).apply(i, DimensionCondition::new));

    @Override
    public boolean test(Entity entity, FormulaContext context) {
        return entity.level().dimension().equals(this.dimension) ^ this.inverted;
    }

    @Override
    public MapCodec<DimensionCondition> codec() {
        return CODEC;
    }
}
