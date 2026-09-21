package com.iafenvoy.mxt.data.artifact;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Persistent ItemStack state for an artifact: whom it belongs to, and how well it has been fed.
 *
 * <p>Which artifact a stack is comes from the item's own definition, and how much aura it holds lives in the
 * shared {@code mxt:spirit_storage} component; neither is repeated here.</p>
 */
public record ArtifactStateComponent(Optional<String> ownerUuid, double nourishment) {
    public static final Codec<ArtifactStateComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("owner_uuid").forGetter(ArtifactStateComponent::ownerUuid),
            Codec.DOUBLE.optionalFieldOf("nourishment", 0.0D).forGetter(ArtifactStateComponent::nourishment)
    ).apply(i, ArtifactStateComponent::new));

    public ArtifactStateComponent {
        if (!Double.isFinite(nourishment) || nourishment < 0.0D) {
            throw new IllegalArgumentException("Artifact state values must be finite and non-negative");
        }
    }

    public static ArtifactStateComponent empty() {
        return new ArtifactStateComponent(Optional.empty(), 0.0D);
    }

    public ArtifactStateComponent withOwner(String owner) {
        return new ArtifactStateComponent(Optional.of(owner), this.nourishment);
    }

    public ArtifactStateComponent withNourishment(double value) {
        return new ArtifactStateComponent(this.ownerUuid, value);
    }
}
