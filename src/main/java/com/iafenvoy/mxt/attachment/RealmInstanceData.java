package com.iafenvoy.mxt.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class RealmInstanceData {
    public static final MapCodec<RealmInstanceData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("definition").forGetter(RealmInstanceData::definition), Codec.LONG.optionalFieldOf("started_at", -1L).forGetter(RealmInstanceData::startedAt), Codec.LONG.optionalFieldOf("expires_at", -1L).forGetter(RealmInstanceData::expiresAt),
            UUIDUtilCodec.CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(RealmInstanceData::members)
    ).apply(instance, RealmInstanceData::decode));
    public static final Codec<RealmInstanceData> CODEC = MAP_CODEC.codec();
    private Identifier definition;
    private long startedAt;
    private long expiresAt;
    private final List<UUID> members;

    public RealmInstanceData() {
        this(Optional.empty(), -1L, -1L, List.of());
    }

    private RealmInstanceData(Optional<Identifier> definition, long startedAt, long expiresAt, List<UUID> members) {
        this.definition = definition.orElse(null);
        this.startedAt = startedAt;
        this.expiresAt = expiresAt;
        this.members = new ArrayList<>(members);
    }

    private static RealmInstanceData decode(Optional<Identifier> definition, long startedAt, long expiresAt, List<UUID> members) {
        return new RealmInstanceData(definition, startedAt, expiresAt, members);
    }

    public Optional<Identifier> definition() {
        return Optional.ofNullable(this.definition);
    }

    public long startedAt() {
        return this.startedAt;
    }

    public long expiresAt() {
        return this.expiresAt;
    }

    public List<UUID> members() {
        return List.copyOf(this.members);
    }

    public boolean active() {
        return this.definition != null;
    }

    public void start(Identifier id, long gameTime, long duration) {
        this.definition = id;
        this.startedAt = gameTime;
        this.expiresAt = duration <= 0L ? -1L : Math.addExact(gameTime, duration);
        this.members.clear();
    }

    public boolean add(UUID id, int max) {
        if (this.members.contains(id)) return true;
        if (this.members.size() >= max) return false;
        this.members.add(id);
        return true;
    }

    public void remove(UUID id) {
        this.members.remove(id);
    }

    public boolean expired(long gameTime) {
        return this.expiresAt >= 0L && gameTime >= this.expiresAt;
    }

    public void clear() {
        this.definition = null;
        this.startedAt = -1L;
        this.expiresAt = -1L;
        this.members.clear();
    }

    private static final class UUIDUtilCodec {
        private static final Codec<UUID> CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
    }
}
