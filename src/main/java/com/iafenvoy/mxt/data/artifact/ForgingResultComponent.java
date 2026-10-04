package com.iafenvoy.mxt.data.artifact;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * Immutable item component written only when a forging session successfully completes. It records what the smith
 * did; the tier the session settled on is written to the one quality component instead.
 */
public record ForgingResultComponent(Identifier blueprint, int finalValue, int actualSteps, int optimalSteps,
                                     int extraSteps) {
    public static final Codec<ForgingResultComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("blueprint").forGetter(ForgingResultComponent::blueprint),
            Codec.INT.fieldOf("final_value").forGetter(ForgingResultComponent::finalValue),
            Codec.INT.fieldOf("actual_steps").forGetter(ForgingResultComponent::actualSteps),
            Codec.INT.fieldOf("optimal_steps").forGetter(ForgingResultComponent::optimalSteps),
            Codec.INT.fieldOf("extra_steps").forGetter(ForgingResultComponent::extraSteps)
    ).apply(i, ForgingResultComponent::new));

    public ForgingResultComponent {
        if (actualSteps < 0 || optimalSteps < 0 || extraSteps < 0 || actualSteps - optimalSteps != extraSteps) {
            throw new IllegalArgumentException("Invalid forging result step counts");
        }
    }
}
