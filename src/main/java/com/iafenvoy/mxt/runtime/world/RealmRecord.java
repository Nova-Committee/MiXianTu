package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.data.realm.RealmGeneration.Existing;
import com.iafenvoy.mxt.data.realm.RealmInstance;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * One realm instance: the definition it was opened from, where it lives, who claimed it and who is inside.
 * <p>
 * The membership list is part of the record so one type describes an instance, but it is cleared when a world
 * is loaded: after a restart nobody is standing inside.
 */
public record RealmRecord(Holder<RealmInstance> definition, int index, ResourceKey<Level> dimension, long seed,
                          Optional<UUID> owner, long startedAt, long expiresAt, Optional<Vec3> anchor,
                          boolean prepared, List<UUID> members) {
    public static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    public static final Codec<RealmRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryFixedCodec.create(MxtResourceKeys.REALM_INSTANCE).fieldOf("definition").forGetter(RealmRecord::definition),
            Codec.INT.optionalFieldOf("index", 0).forGetter(RealmRecord::index),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(RealmRecord::dimension),
            Codec.LONG.optionalFieldOf("seed", 0L).forGetter(RealmRecord::seed),
            UUID_CODEC.optionalFieldOf("owner").forGetter(RealmRecord::owner),
            Codec.LONG.optionalFieldOf("started_at", -1L).forGetter(RealmRecord::startedAt),
            Codec.LONG.optionalFieldOf("expires_at", -1L).forGetter(RealmRecord::expiresAt),
            Vec3.CODEC.optionalFieldOf("anchor").forGetter(RealmRecord::anchor),
            Codec.BOOL.optionalFieldOf("prepared", false).forGetter(RealmRecord::prepared),
            UUID_CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(RealmRecord::members)
    ).apply(i, RealmRecord::new));

    public RealmInstance instance() {
        return this.definition.value();
    }

    public boolean expired(long gameTime) {
        return this.expiresAt >= 0L && gameTime >= this.expiresAt;
    }

    public boolean isOwner(UUID id) {
        return this.owner.filter(id::equals).isPresent();
    }

    public boolean holds(UUID id) {
        return this.members.contains(id);
    }

    public boolean full() {
        return this.members.size() >= this.instance().memberLimit();
    }

    public boolean empty() {
        return this.members.isEmpty();
    }

    // A claimed realm keeps the terrain its owner will come back to, and a realm built on an existing
    // dimension has terrain that was never ours to discard.
    public boolean persists() {
        return this.instance().owned() || this.instance().generation() instanceof Existing;
    }

    // Nobody inside: the clock stops until the next arrival, and the claim and terrain stay.
    public RealmRecord idle() {
        return new RealmRecord(this.definition, this.index, this.dimension, this.seed, this.owner, -1L, -1L,
                this.anchor, this.prepared, List.of());
    }

    public RealmRecord with(List<UUID> value) {
        return new RealmRecord(this.definition, this.index, this.dimension, this.seed, this.owner, this.startedAt,
                this.expiresAt, this.anchor, this.prepared, List.copyOf(value));
    }

    public RealmRecord withOwner(UUID id) {
        return new RealmRecord(this.definition, this.index, this.dimension, this.seed, Optional.of(id), this.startedAt,
                this.expiresAt, this.anchor, this.prepared, this.members);
    }

    public RealmRecord withAnchor(Vec3 value) {
        return new RealmRecord(this.definition, this.index, this.dimension, this.seed, this.owner, this.startedAt,
                this.expiresAt, Optional.of(value), this.prepared, this.members);
    }

    public RealmRecord asPrepared() {
        return new RealmRecord(this.definition, this.index, this.dimension, this.seed, this.owner, this.startedAt,
                this.expiresAt, this.anchor, true, this.members);
    }

    // A timed realm restarts its clock, so a claimed realm cannot expire while it is dormant.
    public RealmRecord restarted(long gameTime) {
        long duration = this.instance().durationTicks();
        return new RealmRecord(this.definition, this.index, this.dimension, this.seed, this.owner, gameTime,
                duration <= 0L ? -1L : gameTime + duration, this.anchor, this.prepared, List.of());
    }

    public RealmRecord dormant() {
        return new RealmRecord(this.definition, this.index, this.dimension, this.seed, this.owner, this.startedAt,
                this.expiresAt, this.anchor, this.prepared, List.of());
    }
}
