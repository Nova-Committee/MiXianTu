package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-owned flight mount state. What is saved is the flight <em>attribute</em> value, not the deprecated
 * {@code Abilities#mayfly} flag: if the attribute stayed raised after dismounting, the player would keep flying.
 *
 * <p>The archetype is the ability the flight was started with, so two artifacts that both declare flight are told
 * apart by which ability is up rather than by which item happens to be held.
 */
public final class FlightAttachment extends ShouldSyncAttachment {
    public static final MapCodec<FlightAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.lenientOptionalFieldOf("active", false).forGetter(FlightAttachment::active),
            Ability.CODEC.lenientOptionalFieldOf("archetype").forGetter(FlightAttachment::archetype),
            Codec.LONG.lenientOptionalFieldOf("started_at", 0L).forGetter(FlightAttachment::startedAt),
            Codec.DOUBLE.lenientOptionalFieldOf("previous_flight", 0.0D).forGetter(FlightAttachment::previousFlight),
            Codec.BOOL.lenientOptionalFieldOf("previous_flying", false).forGetter(FlightAttachment::previousFlying),
            Codec.FLOAT.lenientOptionalFieldOf("previous_flying_speed", 0.05F).forGetter(FlightAttachment::previousFlyingSpeed),
            Codec.STRING.lenientOptionalFieldOf("vehicle").forGetter(FlightAttachment::vehicleRaw)
    ).apply(i, FlightAttachment::new));
    private boolean active;
    private Holder<Ability> archetype;
    private long startedAt;
    private double previousFlight;
    private boolean previousFlying;
    private float previousFlyingSpeed;
    private String vehicle;

    public FlightAttachment() {
    }

    private FlightAttachment(boolean active, Optional<Holder<Ability>> archetype, long startedAt, double previousFlight, boolean previousFlying, float previousFlyingSpeed, Optional<String> vehicle) {
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

    public Optional<Holder<Ability>> archetype() {
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

    public void start(Holder<Ability> archetype, long gameTime, double flight, boolean flying, float flyingSpeed, UUID vehicle) {
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
