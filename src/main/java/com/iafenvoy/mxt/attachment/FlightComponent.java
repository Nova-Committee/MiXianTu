package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.artifact.ItemArchetype;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-owned flight mount state.
 */
public final class FlightComponent {
    public static final MapCodec<FlightComponent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("active", false).forGetter(FlightComponent::active),
            ItemArchetype.CODEC.optionalFieldOf("archetype").forGetter(FlightComponent::archetype),
            Codec.LONG.optionalFieldOf("started_at", 0L).forGetter(FlightComponent::startedAt),
            Codec.BOOL.optionalFieldOf("previous_mayfly", false).forGetter(FlightComponent::previousMayfly),
            Codec.BOOL.optionalFieldOf("previous_flying", false).forGetter(FlightComponent::previousFlying),
            Codec.FLOAT.optionalFieldOf("previous_flying_speed", 0.05F).forGetter(FlightComponent::previousFlyingSpeed),
            Codec.STRING.optionalFieldOf("vehicle").forGetter(FlightComponent::vehicleRaw)
    ).apply(i, FlightComponent::new));
    private boolean active;
    private Holder<ItemArchetype> archetype;
    private long startedAt;
    private boolean previousMayfly;
    private boolean previousFlying;
    private float previousFlyingSpeed;
    private String vehicle;

    public FlightComponent() {
    }

    private FlightComponent(boolean active, Optional<Holder<ItemArchetype>> archetype, long startedAt, boolean previousMayfly, boolean previousFlying, float previousFlyingSpeed, Optional<String> vehicle) {
        this.active = active;
        this.archetype = archetype.orElse(null);
        this.startedAt = startedAt;
        this.previousMayfly = previousMayfly;
        this.previousFlying = previousFlying;
        this.previousFlyingSpeed = previousFlyingSpeed;
        this.vehicle = vehicle.orElse(null);
    }

    public boolean active() {
        return this.active;
    }

    public Optional<Holder<ItemArchetype>> archetype() {
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

    public void start(Holder<ItemArchetype> archetype, long gameTime, boolean mayfly, boolean flying, float flyingSpeed, UUID vehicle) {
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
