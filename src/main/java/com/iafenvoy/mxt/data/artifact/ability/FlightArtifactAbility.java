package com.iafenvoy.mxt.data.artifact.ability;

import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Lets the artifact carry its holder: the speed the flying mount moves at, and what a tick of riding costs.
 * {@link com.iafenvoy.mxt.data.artifact.Artifact} refuses a second entry of this kind, so "how fast is this
 * artifact" always has one answer.
 */
public record FlightArtifactAbility(NumberProvider speed, List<ResourceCost> costs) implements ArtifactAbility {
    public static final MapCodec<FlightArtifactAbility> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("speed").forGetter(FlightArtifactAbility::speed),
            ResourceCost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(FlightArtifactAbility::costs)
    ).apply(i, FlightArtifactAbility::new));

    @Override
    public MapCodec<FlightArtifactAbility> codec() {
        return CODEC;
    }
}
