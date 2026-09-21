package com.iafenvoy.mxt.data.artifact.ability;

import com.mojang.serialization.MapCodec;

/**
 * An entry that declares nothing, written explicitly as {@code "type": "mxt:empty"}. It is the default entry of
 * {@code mxt:artifact_ability_type} and the placeholder to use when a slot in an {@code abilities} list must
 * exist but do nothing; {@code type} itself stays required.
 */
public enum EmptyArtifactAbility implements ArtifactAbility {
    INSTANCE;

    public static final MapCodec<EmptyArtifactAbility> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<EmptyArtifactAbility> codec() {
        return CODEC;
    }
}
