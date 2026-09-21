package com.iafenvoy.mxt.data.artifact.ability;

import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;

/**
 * Gives the artifact an inventory of its own; the slot count is the whole declaration and the contents live in
 * the stack's storage component. {@link com.iafenvoy.mxt.data.artifact.Artifact} refuses a second entry of this
 * kind, so "how many slots has it" always has one answer.
 */
public record StorageArtifactAbility(NumberProvider slots) implements ArtifactAbility {
    public static final MapCodec<StorageArtifactAbility> CODEC =
            NumberProvider.CODEC.fieldOf("slots").xmap(StorageArtifactAbility::new, StorageArtifactAbility::slots);

    @Override
    public MapCodec<StorageArtifactAbility> codec() {
        return CODEC;
    }
}
