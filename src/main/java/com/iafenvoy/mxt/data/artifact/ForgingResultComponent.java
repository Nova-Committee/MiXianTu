package com.iafenvoy.mxt.data.artifact;

import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

/**
 * Immutable item component written only when a forging session successfully completes.
 */
public record ForgingResultComponent(Identifier blueprint, int finalValue, int actualSteps, int optimalSteps, int extraSteps,
                                Holder<ItemQuality> quality) {
    public static final Codec<ForgingResultComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("blueprint").forGetter(ForgingResultComponent::blueprint),
            Codec.INT.fieldOf("final_value").forGetter(ForgingResultComponent::finalValue),
            Codec.INT.fieldOf("actual_steps").forGetter(ForgingResultComponent::actualSteps),
            Codec.INT.fieldOf("optimal_steps").forGetter(ForgingResultComponent::optimalSteps),
            Codec.INT.fieldOf("extra_steps").forGetter(ForgingResultComponent::extraSteps),
            ItemQuality.CODEC.fieldOf("quality").forGetter(ForgingResultComponent::quality)
    ).apply(i, ForgingResultComponent::new));

    public ForgingResultComponent {
        if (actualSteps < 0 || optimalSteps < 0 || extraSteps < 0 || actualSteps - optimalSteps != extraSteps) {
            throw new IllegalArgumentException("Invalid forging result step counts");
        }
    }
}
