package com.iafenvoy.mxt.data.artifact.ability;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.function.Function;

/**
 * One capability an artifact grants, selected by the datapack {@code type} of its entry in {@code abilities}.
 *
 * <p>Same division as every other built-in dispatch here: the code owns the behaviour and the entry only picks
 * one. The skills themselves stay ordinary {@code ability} definitions.</p>
 */
public interface ArtifactAbility {
    Codec<ArtifactAbility> CODEC = MxtRegistries.ARTIFACT_ABILITY_TYPE.byNameCodec().dispatch("type", ArtifactAbility::codec, Function.identity());

    MapCodec<? extends ArtifactAbility> codec();
}
