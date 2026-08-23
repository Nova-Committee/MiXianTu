package com.iafenvoy.mxt.data.artifact;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Persistent ItemStack state for ownership, nurturing and an optional archetype definition.
 */
public record ArtifactStateComponent(Optional<String> ownerUuid, Optional<Identifier> archetype, double nourishment,
                                     double spiritEnergy) {
    public static final Codec<ArtifactStateComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("owner_uuid").forGetter(ArtifactStateComponent::ownerUuid),
            Identifier.CODEC.optionalFieldOf("archetype").forGetter(ArtifactStateComponent::archetype),
            Codec.DOUBLE.optionalFieldOf("nourishment", 0.0D).forGetter(ArtifactStateComponent::nourishment),
            Codec.DOUBLE.optionalFieldOf("spirit_energy", 0.0D).forGetter(ArtifactStateComponent::spiritEnergy)
    ).apply(i, ArtifactStateComponent::new));

    public ArtifactStateComponent {
        if (!Double.isFinite(nourishment) || nourishment < 0.0D || !Double.isFinite(spiritEnergy) || spiritEnergy < 0.0D) {
            throw new IllegalArgumentException("Artifact state values must be finite and non-negative");
        }
    }

    public static ArtifactStateComponent empty() {
        return new ArtifactStateComponent(Optional.empty(), Optional.empty(), 0.0D, 0.0D);
    }

    public ArtifactStateComponent withOwner(String owner) {
        return new ArtifactStateComponent(Optional.of(owner), this.archetype, this.nourishment, this.spiritEnergy);
    }

    public ArtifactStateComponent withEnergy(double energy) {
        return new ArtifactStateComponent(this.ownerUuid, this.archetype, this.nourishment, energy);
    }

    public ArtifactStateComponent withNourishment(double value) {
        return new ArtifactStateComponent(this.ownerUuid, this.archetype, value, this.spiritEnergy);
    }
}
