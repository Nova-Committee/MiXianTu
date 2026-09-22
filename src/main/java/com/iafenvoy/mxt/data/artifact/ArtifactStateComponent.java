package com.iafenvoy.mxt.data.artifact;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Persistent ItemStack state for an artifact: whom it belongs to, what that owner is called, and how well it has
 * been fed. Which artifact a stack is comes from the item's definition and how much aura it holds lives in the
 * shared {@code mxt:spirit_storage} component; neither is repeated here. The UUID is the identity every ownership
 * question is asked of, while the name is display only, written once by whoever claimed the stack.
 */
public record ArtifactStateComponent(Optional<String> ownerUuid, Optional<String> ownerName, double nourishment) {
    public static final Codec<ArtifactStateComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("owner_uuid").forGetter(ArtifactStateComponent::ownerUuid),
            Codec.STRING.optionalFieldOf("owner_name").forGetter(ArtifactStateComponent::ownerName),
            Codec.DOUBLE.optionalFieldOf("nourishment", 0.0D).forGetter(ArtifactStateComponent::nourishment)
    ).apply(i, ArtifactStateComponent::new));

    public ArtifactStateComponent {
        if (!Double.isFinite(nourishment) || nourishment < 0.0D) {
            throw new IllegalArgumentException("Artifact state values must be finite and non-negative");
        }
    }

    public static ArtifactStateComponent empty() {
        return new ArtifactStateComponent(Optional.empty(), Optional.empty(), 0.0D);
    }

    // ownerName never replaces the UUID as the answer to "whose is this".
    public ArtifactStateComponent withOwner(String ownerUuid, Optional<String> ownerName) {
        return new ArtifactStateComponent(Optional.of(ownerUuid), ownerName, this.nourishment);
    }

    public ArtifactStateComponent withNourishment(double value) {
        return new ArtifactStateComponent(this.ownerUuid, this.ownerName, value);
    }
}
