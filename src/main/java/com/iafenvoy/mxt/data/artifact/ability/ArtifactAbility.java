package com.iafenvoy.mxt.data.artifact.ability;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.function.Function;

/**
 * One capability an artifact grants, selected by the datapack {@code type} of its entry in {@code abilities}. The
 * code owns the behaviour and the entry only picks one; the skills themselves stay ordinary ability definitions.
 */
public interface ArtifactAbility {
    Codec<ArtifactAbility> CODEC = MxtRegistries.ARTIFACT_ABILITY_TYPE.byNameCodec().dispatch("type", ArtifactAbility::codec, Function.identity());

    MapCodec<? extends ArtifactAbility> codec();
}
