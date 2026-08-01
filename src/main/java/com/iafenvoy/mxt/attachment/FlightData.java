package com.iafenvoy.mxt.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-owned flight mount state.
 */
public final class FlightData {
    public static final MapCodec<FlightData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("active", false).forGetter(FlightData::active),
            Identifier.CODEC.optionalFieldOf("archetype").forGetter(FlightData::archetype),
            Codec.LONG.optionalFieldOf("started_at", 0L).forGetter(FlightData::startedAt),
            Codec.BOOL.optionalFieldOf("previous_mayfly", false).forGetter(FlightData::previousMayfly),
            Codec.BOOL.optionalFieldOf("previous_flying", false).forGetter(FlightData::previousFlying),
            Codec.FLOAT.optionalFieldOf("previous_flying_speed", 0.05F).forGetter(FlightData::previousFlyingSpeed),
            Codec.STRING.optionalFieldOf("vehicle").forGetter(FlightData::vehicleRaw)
    ).apply(instance, FlightData::decode));
    public static final Codec<FlightData> CODEC = MAP_CODEC.codec();
    private boolean active;
    private Identifier archetype;
    private long startedAt;
    private boolean previousMayfly;
    private boolean previousFlying;
    private float previousFlyingSpeed;
    private String vehicle;

    public FlightData() {
    }

    private FlightData(boolean active, Optional<Identifier> archetype, long startedAt, boolean previousMayfly, boolean previousFlying, float previousFlyingSpeed, Optional<String> vehicle) {
        this.active = active;
        this.archetype = archetype.orElse(null);
        this.startedAt = startedAt;
        this.previousMayfly = previousMayfly;
        this.previousFlying = previousFlying;
        this.previousFlyingSpeed = previousFlyingSpeed;
        this.vehicle = vehicle.orElse(null);
    }

    private static FlightData decode(boolean active, Optional<Identifier> archetype, long startedAt, boolean previousMayfly, boolean previousFlying, float previousFlyingSpeed, Optional<String> vehicle) {
        return new FlightData(active, archetype, startedAt, previousMayfly, previousFlying, previousFlyingSpeed, vehicle);
    }

    public boolean active() {
        return this.active;
    }

    public Optional<Identifier> archetype() {
        return Optional.ofNullable(this.archetype);
    }

    public long startedAt() {
        return this.startedAt;
    }

    public boolean previousMayfly() {
        return this.previousMayfly;
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

    public void start(Identifier archetype, long gameTime, boolean mayfly, boolean flying, float flyingSpeed, UUID vehicle) {
        this.active = true;
        this.archetype = archetype;
        this.startedAt = gameTime;
        this.previousMayfly = mayfly;
        this.previousFlying = flying;
        this.previousFlyingSpeed = flyingSpeed;
        this.vehicle = vehicle.toString();
    }

    public void stop() {
        this.active = false;
        this.archetype = null;
        this.previousMayfly = false;
        this.previousFlying = false;
        this.previousFlyingSpeed = 0.05F;
        this.vehicle = null;
    }
}
