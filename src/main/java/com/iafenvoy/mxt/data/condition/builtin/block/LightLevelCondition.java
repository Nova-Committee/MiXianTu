package com.iafenvoy.mxt.data.condition.builtin.block;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

import java.util.Locale;
import java.util.Optional;

public record LightLevelCondition(Optional<LightLayer> lightType, Comparison comparison) implements BlockCondition {
    private static final Codec<LightLayer> LIGHT_LAYER_CODEC = Codec.STRING.comapFlatMap(value -> {
        try {
            return DataResult.success(LightLayer.valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(() -> "Unknown light type " + value);
        }
    }, value -> value.name().toLowerCase(Locale.ROOT));
    public static final MapCodec<LightLevelCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            LIGHT_LAYER_CODEC.optionalFieldOf("light_type").forGetter(LightLevelCondition::lightType),
            Comparison.CODEC.forGetter(LightLevelCondition::comparison)
    ).apply(instance, LightLevelCondition::new));

    @Override
    public boolean test(Level level, BlockPos pos, FormulaContext context) {
        int light = this.lightType.map(type -> level.getBrightness(type, pos)).orElseGet(() -> level.getMaxLocalRawBrightness(pos));
        return this.comparison.compare(light);
    }

    @Override
    public MapCodec<LightLevelCondition> codec() {
        return CODEC;
    }
}
