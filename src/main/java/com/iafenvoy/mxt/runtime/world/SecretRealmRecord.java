package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.data.secretrealm.SecretRealm;
import com.iafenvoy.mxt.data.secretrealm.builtin.ExistingGeneration;
import com.iafenvoy.saturn.util.codec.OptionalCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.*;

/**
 * One secret realm: the definition it was opened from, where it lives, who claimed it and who is inside.
 * <p>
 * The membership list is part of the record so one type describes an instance, but it is cleared when a world
 * is loaded: after a restart nobody is standing inside.
 */
public record SecretRealmRecord(Holder<SecretRealm> definition, int index, ResourceKey<Level> dimension,
                                OptionalLong seed, Optional<UUID> owner, long startedAt, long expiresAt,
                                Optional<Vec3> anchor, boolean prepared, List<UUID> members, UUID instanceId,
                                Cleanup cleanup) {
    public static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    public static final Codec<SecretRealmRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            SecretRealm.CODEC.fieldOf("definition").forGetter(SecretRealmRecord::definition),
            Codec.INT.lenientOptionalFieldOf("index", 0).forGetter(SecretRealmRecord::index),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(SecretRealmRecord::dimension),
            OptionalCodecs.lenientLongCodec("seed").forGetter(SecretRealmRecord::seed),
            UUID_CODEC.lenientOptionalFieldOf("owner").forGetter(SecretRealmRecord::owner),
            Codec.LONG.lenientOptionalFieldOf("started_at", -1L).forGetter(SecretRealmRecord::startedAt),
            Codec.LONG.lenientOptionalFieldOf("expires_at", -1L).forGetter(SecretRealmRecord::expiresAt),
            Vec3.CODEC.lenientOptionalFieldOf("anchor").forGetter(SecretRealmRecord::anchor),
            Codec.BOOL.lenientOptionalFieldOf("prepared", false).forGetter(SecretRealmRecord::prepared),
            UUID_CODEC.listOf().lenientOptionalFieldOf("members", List.of()).forGetter(SecretRealmRecord::members),
            UUIDUtil.CODEC.optionalFieldOf("instance_id").forGetter(record -> Optional.of(record.instanceId())),
            StringRepresentable.fromEnum(Cleanup::values).optionalFieldOf("cleanup", Cleanup.NONE).forGetter(SecretRealmRecord::cleanup)
    ).apply(i, (definition, index, dimension, seed, owner, started, expires, anchor, prepared, members, identity, cleanup) ->
            new SecretRealmRecord(definition, index, dimension, seed, owner, started, expires, anchor, prepared, members,
                    identity.orElseGet(UUID::randomUUID), cleanup)));

    public enum Cleanup implements StringRepresentable {
        NONE, IDLE, DESTROY, ABORT;

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public SecretRealmRecord(Holder<SecretRealm> definition, int index, ResourceKey<Level> dimension,
                             OptionalLong seed, Optional<UUID> owner, long startedAt, long expiresAt,
                             Optional<Vec3> anchor, boolean prepared, List<UUID> members, UUID identity) {
        this(definition, index, dimension, seed, owner, startedAt, expiresAt, anchor, prepared, members, identity, Cleanup.NONE);
    }

    public SecretRealmRecord withCleanup(Cleanup value) {
        return new SecretRealmRecord(this.definition, this.index, this.dimension, this.seed, this.owner, this.startedAt,
                this.expiresAt, this.anchor, this.prepared, this.members, this.instanceId, value);
    }

    public SecretRealmRecord(Holder<SecretRealm> definition, int index, ResourceKey<Level> dimension,
                             OptionalLong seed, Optional<UUID> owner, long startedAt, long expiresAt,
                             Optional<Vec3> anchor, boolean prepared, List<UUID> members) {
        this(definition, index, dimension, seed, owner, startedAt, expiresAt, anchor, prepared, members, UUID.randomUUID());
    }

    public SecretRealm instance() {
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
        return this.members.size() >= this.instance().maxMembers();
    }

    public boolean empty() {
        return this.members.isEmpty();
    }

    // A claimed secret realm keeps the terrain its owner will come back to, and a secret realm built on an existing
    // dimension has terrain that was never ours to discard.
    public boolean persists() {
        return this.instance().owned() || this.instance().generation() instanceof ExistingGeneration;
    }

    // Nobody inside: the clock stops until the next arrival, and the claim and terrain stay.
    public SecretRealmRecord idle() {
        return new SecretRealmRecord(this.definition, this.index, this.dimension, this.seed, this.owner, -1L, -1L,
                this.anchor, this.prepared, List.of(), this.instanceId);
    }

    public SecretRealmRecord with(List<UUID> value) {
        return new SecretRealmRecord(this.definition, this.index, this.dimension, this.seed, this.owner, this.startedAt,
                this.expiresAt, this.anchor, this.prepared, List.copyOf(value), this.instanceId, this.cleanup);
    }

    public SecretRealmRecord withOwner(UUID id) {
        return new SecretRealmRecord(this.definition, this.index, this.dimension, this.seed, Optional.of(id), this.startedAt,
                this.expiresAt, this.anchor, this.prepared, this.members, this.instanceId);
    }

    public SecretRealmRecord withAnchor(Vec3 value) {
        return new SecretRealmRecord(this.definition, this.index, this.dimension, this.seed, this.owner, this.startedAt,
                this.expiresAt, Optional.of(value), this.prepared, this.members, this.instanceId);
    }

    public SecretRealmRecord asPrepared() {
        return new SecretRealmRecord(this.definition, this.index, this.dimension, this.seed, this.owner, this.startedAt,
                this.expiresAt, this.anchor, true, this.members, this.instanceId);
    }

    // A timed secret realm restarts its clock, so a claimed secret realm cannot expire while it is dormant.
    public SecretRealmRecord restarted(long gameTime) {
        long duration = this.instance().durationTicks();
        return new SecretRealmRecord(this.definition, this.index, this.dimension, this.seed, this.owner, gameTime,
                duration <= 0L ? -1L : gameTime + duration, this.anchor, this.prepared, List.of(), this.instanceId);
    }

    public SecretRealmRecord dormant() {
        return new SecretRealmRecord(this.definition, this.index, this.dimension, this.seed, this.owner, this.startedAt,
                this.expiresAt, this.anchor, this.prepared, List.of(), this.instanceId, this.cleanup);
    }
}
