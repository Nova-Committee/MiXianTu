package com.iafenvoy.mxt.data.artifact;

import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

/**
 * Immutable item component written only when a forging session successfully completes. It records what the smith
 * did; the tier the session settled on is written to the one quality component instead. The blueprint is the
 * definition's own holder, so an entry whose blueprint left the pack drops the component rather than keeping an id
 * nothing can resolve.
 */
public record ForgingResultComponent(Holder<ForgingBlueprint> blueprint, int finalValue, int actualSteps,
                                     int optimalSteps,
                                     int extraSteps) {
    public static final Codec<ForgingResultComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            ForgingBlueprint.CODEC.fieldOf("blueprint").forGetter(ForgingResultComponent::blueprint),
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
