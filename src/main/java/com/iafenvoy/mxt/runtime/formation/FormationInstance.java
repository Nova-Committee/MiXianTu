package com.iafenvoy.mxt.runtime.formation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistable logical formation instance; block structure validation remains an adapter concern.
 */
public final class FormationInstance {
    private final Identifier formation;
    private final double radius;
    private final Optional<UUID> owner;
    private boolean active;
    private long maintenanceCount;

    FormationInstance(Identifier formation, double radius) {
        this(formation, radius, Optional.empty(), true, 0L);
    }

    FormationInstance(Identifier formation, double radius, UUID owner) {
        this(formation, radius, Optional.of(owner), true, 0L);
    }

    private FormationInstance(@NotNull Identifier formation, double radius, @NotNull Optional<UUID> owner, boolean active, long maintenanceCount) {
        this.formation = formation;
        if (!Double.isFinite(radius) || radius <= 0.0D || maintenanceCount < 0L)
            throw new IllegalArgumentException("Invalid formation instance state");
        this.radius = radius;
        this.owner = owner;
        this.active = active;
        this.maintenanceCount = maintenanceCount;
    }

    public Identifier formation() {
        return this.formation;
    }

    public double radius() {
        return this.radius;
    }

    public Optional<UUID> owner() {
        return this.owner;
    }

    public boolean active() {
        return this.active;
    }

    public long maintenanceCount() {
        return this.maintenanceCount;
    }

    void maintained() {
        this.maintenanceCount++;
    }

    void deactivate() {
        this.active = false;
    }

    public Snapshot snapshot() {
        return new Snapshot(this.formation, this.radius, this.owner, this.active, this.maintenanceCount);
    }

    public static FormationInstance restore(Snapshot snapshot) {
        return new FormationInstance(snapshot.formation(), snapshot.radius(), snapshot.owner(), snapshot.active(), snapshot.maintenanceCount());
    }

    /**
     * Persistent runtime state; structural validation and loaded-chunk ownership remain world adapters.
     */
    public record Snapshot(@NotNull Identifier formation, double radius, @NotNull Optional<UUID> owner, boolean active,
                           long maintenanceCount) {
        public static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("formation").forGetter(Snapshot::formation), Codec.DOUBLE.fieldOf("radius").forGetter(Snapshot::radius), UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(Snapshot::owner),
                Codec.BOOL.optionalFieldOf("active", true).forGetter(Snapshot::active), Codec.LONG.optionalFieldOf("maintenance_count", 0L).forGetter(Snapshot::maintenanceCount)
        ).apply(i, Snapshot::new));

        public Snapshot(Identifier formation, double radius, boolean active, long maintenanceCount) {
            this(formation, radius, Optional.empty(), active, maintenanceCount);
        }

        public Snapshot {
            if (!Double.isFinite(radius) || radius <= 0.0D || maintenanceCount < 0L)
                throw new IllegalArgumentException("Invalid formation snapshot");
        }
    }
}
