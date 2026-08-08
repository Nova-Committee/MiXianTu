package com.iafenvoy.mxt.data.artifact;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Persistent ItemStack state for ownership, nurturing and an optional archetype definition.
 */
public record ArtifactStateData(Optional<String> ownerUuid, Optional<Identifier> archetype, double nourishment,
                                double spiritEnergy) {
    public static final Codec<ArtifactStateData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("owner_uuid").forGetter(ArtifactStateData::ownerUuid),
            Identifier.CODEC.optionalFieldOf("archetype").forGetter(ArtifactStateData::archetype),
            Codec.DOUBLE.optionalFieldOf("nourishment", 0.0D).forGetter(ArtifactStateData::nourishment),
            Codec.DOUBLE.optionalFieldOf("spirit_energy", 0.0D).forGetter(ArtifactStateData::spiritEnergy)
    ).apply(instance, ArtifactStateData::new));

    public ArtifactStateData {
        if (!Double.isFinite(nourishment) || nourishment < 0.0D || !Double.isFinite(spiritEnergy) || spiritEnergy < 0.0D) {
            throw new IllegalArgumentException("Artifact state values must be finite and non-negative");
        }
    }

    public static ArtifactStateData empty() {
        return new ArtifactStateData(Optional.empty(), Optional.empty(), 0.0D, 0.0D);
    }

    public ArtifactStateData withOwner(String owner) {
        return new ArtifactStateData(Optional.of(owner), this.archetype, this.nourishment, this.spiritEnergy);
    }

    public ArtifactStateData withEnergy(double energy) {
        return new ArtifactStateData(this.ownerUuid, this.archetype, this.nourishment, energy);
    }

    public ArtifactStateData withNourishment(double value) {
        return new ArtifactStateData(this.ownerUuid, this.archetype, value, this.spiritEnergy);
    }
}
