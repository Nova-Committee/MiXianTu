package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.artifact.Artifact;
import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-owned flight mount state.
 *
 * <p>What is saved is the flight <em>attribute</em> value, not the deprecated {@code Abilities#mayfly}
 * flag; if the attribute stayed raised after dismounting the player would keep flying.
 */
public final class FlightAttachment extends ShouldSyncAttachment {
    public static final MapCodec<FlightAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("active", false).forGetter(FlightAttachment::active),
            Artifact.CODEC.optionalFieldOf("archetype").forGetter(FlightAttachment::archetype),
            Codec.LONG.optionalFieldOf("started_at", 0L).forGetter(FlightAttachment::startedAt),
            Codec.DOUBLE.optionalFieldOf("previous_flight", 0.0D).forGetter(FlightAttachment::previousFlight),
            Codec.BOOL.optionalFieldOf("previous_flying", false).forGetter(FlightAttachment::previousFlying),
            Codec.FLOAT.optionalFieldOf("previous_flying_speed", 0.05F).forGetter(FlightAttachment::previousFlyingSpeed),
            Codec.STRING.optionalFieldOf("vehicle").forGetter(FlightAttachment::vehicleRaw)
    ).apply(i, FlightAttachment::new));
    private boolean active;
    private Holder<Artifact> archetype;
    private long startedAt;
    private double previousFlight;
    private boolean previousFlying;
    private float previousFlyingSpeed;
    private String vehicle;

    public FlightAttachment() {
    }

    private FlightAttachment(boolean active, Optional<Holder<Artifact>> archetype, long startedAt, double previousFlight, boolean previousFlying, float previousFlyingSpeed, Optional<String> vehicle) {
        this.active = active;
        this.archetype = archetype.orElse(null);
        this.startedAt = startedAt;
        this.previousFlight = previousFlight;
        this.previousFlying = previousFlying;
        this.previousFlyingSpeed = previousFlyingSpeed;
        this.vehicle = vehicle.orElse(null);
    }

    public boolean active() {
        return this.active;
    }

    public Optional<Holder<Artifact>> archetype() {
        return Optional.ofNullable(this.archetype);
    }

    public long startedAt() {
        return this.startedAt;
    }

    public double previousFlight() {
        return this.previousFlight;
    }

    public boolean previousFlying() {
        return this.previousFlying;
    }

    public float previousFlyingSpeed() {
        return this.previousFlyingSpeed;
    }

    public Optional<UUID> vehicle() {
        try {
            return this.vehicle == null ? Optional.empty() : Optional.of(UUID.fromString(this.vehicle));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private Optional<String> vehicleRaw() {
        return Optional.ofNullable(this.vehicle);
    }

    public void start(Holder<Artifact> archetype, long gameTime, double flight, boolean flying, float flyingSpeed, UUID vehicle) {
        this.active = true;
        this.archetype = archetype;
        this.startedAt = gameTime;
        this.previousFlight = flight;
        this.previousFlying = flying;
        this.previousFlyingSpeed = flyingSpeed;
        this.vehicle = vehicle.toString();
        this.markDirty();
    }

    public void stop() {
        this.active = false;
        this.archetype = null;
        this.previousFlight = 0.0D;
        this.previousFlying = false;
        this.previousFlyingSpeed = 0.05F;
        this.vehicle = null;
        this.markDirty();
    }
}
