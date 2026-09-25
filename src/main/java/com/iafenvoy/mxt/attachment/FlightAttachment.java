package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-owned flight mount state. What is saved is the flight <em>attribute</em> value, not the deprecated
 * {@code Abilities#mayfly} flag: if the attribute stayed raised after dismounting, the player would keep flying.
 *
 * <p>The archetype is the skill the flight was started with, and the vehicle is the mount entry that skill picked up:
 * two artifacts that both declare a mount are told apart by which one is underfoot rather than by which is held. The
 * artifact itself rides inside the mount entity, so nothing about the item needs to be kept here.
 */
public final class FlightAttachment extends ShouldSyncAttachment {
    public static final MapCodec<FlightAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.lenientOptionalFieldOf("active", false).forGetter(FlightAttachment::active),
            Ability.CODEC.lenientOptionalFieldOf("archetype").forGetter(FlightAttachment::archetype),
            Identifier.CODEC.lenientOptionalFieldOf("vehicle").forGetter(FlightAttachment::vehicle),
            Codec.LONG.lenientOptionalFieldOf("started_at", 0L).forGetter(FlightAttachment::startedAt),
            Codec.DOUBLE.lenientOptionalFieldOf("previous_flight", 0.0D).forGetter(FlightAttachment::previousFlight),
            Codec.BOOL.lenientOptionalFieldOf("previous_flying", false).forGetter(FlightAttachment::previousFlying),
            Codec.FLOAT.lenientOptionalFieldOf("previous_flying_speed", 0.05F).forGetter(FlightAttachment::previousFlyingSpeed),
            Codec.STRING.lenientOptionalFieldOf("vehicle_uuid").forGetter(FlightAttachment::mountRaw)
    ).apply(i, FlightAttachment::new));
    private boolean active;
    private Holder<Ability> archetype;
    private Identifier vehicle;
    private long startedAt;
    private double previousFlight;
    private boolean previousFlying;
    private float previousFlyingSpeed;
    private String mount;
    // Not saved: a key that is held when the world closes is not held when it opens again.
    private boolean descend;

    public FlightAttachment() {
    }

    private FlightAttachment(boolean active, Optional<Holder<Ability>> archetype, Optional<Identifier> vehicle, long startedAt, double previousFlight, boolean previousFlying, float previousFlyingSpeed, Optional<String> mount) {
        this.active = active;
        this.archetype = archetype.orElse(null);
        this.vehicle = vehicle.orElse(null);
        this.startedAt = startedAt;
        this.previousFlight = previousFlight;
        this.previousFlying = previousFlying;
        this.previousFlyingSpeed = previousFlyingSpeed;
        this.mount = mount.orElse(null);
    }

    public boolean active() {
        return this.active;
    }

    public Optional<Holder<Ability>> archetype() {
        return Optional.ofNullable(this.archetype);
    }

    public Optional<Identifier> vehicle() {
        return Optional.ofNullable(this.vehicle);
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

    public boolean descend() {
        return this.descend;
    }

    public void setDescend(boolean descend) {
        this.descend = descend;
    }

    public Optional<UUID> mount() {
        try {
            return this.mount == null ? Optional.empty() : Optional.of(UUID.fromString(this.mount));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private Optional<String> mountRaw() {
        return Optional.ofNullable(this.mount);
    }

    public void start(Holder<Ability> archetype, Identifier vehicle, long gameTime, double flight, boolean flying, float flyingSpeed, UUID mount) {
        this.active = true;
        this.archetype = archetype;
        this.vehicle = vehicle;
        this.startedAt = gameTime;
        this.previousFlight = flight;
        this.previousFlying = flying;
        this.previousFlyingSpeed = flyingSpeed;
        this.mount = mount.toString();
        this.markDirty();
    }

    public void stop() {
        this.active = false;
        this.archetype = null;
        this.vehicle = null;
        this.previousFlight = 0.0D;
        this.previousFlying = false;
        this.previousFlyingSpeed = 0.05F;
        this.mount = null;
        this.descend = false;
        this.markDirty();
    }
}
