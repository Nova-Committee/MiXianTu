package com.iafenvoy.mxt.data.artifact;

import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

/**
 * Immutable item component written only when a forging session successfully completes.
 */
public record ForgingResultData(Identifier blueprint, int finalValue, int actualSteps, int optimalSteps, int extraSteps,
                                Holder<ItemQuality> quality) {
    public static final Codec<ForgingResultData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("blueprint").forGetter(ForgingResultData::blueprint),
            Codec.INT.fieldOf("final_value").forGetter(ForgingResultData::finalValue),
            Codec.INT.fieldOf("actual_steps").forGetter(ForgingResultData::actualSteps),
            Codec.INT.fieldOf("optimal_steps").forGetter(ForgingResultData::optimalSteps),
            Codec.INT.fieldOf("extra_steps").forGetter(ForgingResultData::extraSteps),
            ItemQuality.CODEC.fieldOf("quality").forGetter(ForgingResultData::quality)
    ).apply(i, ForgingResultData::new));

    public ForgingResultData {
        if (actualSteps < 0 || optimalSteps < 0 || extraSteps < 0 || actualSteps - optimalSteps != extraSteps) {
            throw new IllegalArgumentException("Invalid forging result step counts");
        }
    }
}
