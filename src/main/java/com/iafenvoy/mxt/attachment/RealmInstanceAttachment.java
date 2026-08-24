package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.data.RealmInstance;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.List;
import java.util.LinkedList;
import java.util.Optional;
import java.util.UUID;

public final class RealmInstanceAttachment {
    public static final MapCodec<RealmInstanceAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryFixedCodec.create(MxtResourceKeys.REALM_INSTANCE).optionalFieldOf("definition").forGetter(RealmInstanceAttachment::definition), Codec.LONG.optionalFieldOf("started_at", -1L).forGetter(RealmInstanceAttachment::startedAt), Codec.LONG.optionalFieldOf("expires_at", -1L).forGetter(RealmInstanceAttachment::expiresAt),
            UUIDUtilCodec.CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(RealmInstanceAttachment::members)
    ).apply(i, RealmInstanceAttachment::new));
    private Holder<RealmInstance> definition;
    private long startedAt, expiresAt;
    private final List<UUID> members;

    public RealmInstanceAttachment() {
        this(Optional.empty(), -1L, -1L, List.of());
    }

    private RealmInstanceAttachment(Optional<Holder<RealmInstance>> definition, long startedAt, long expiresAt, List<UUID> members) {
        this.definition = definition.orElse(null);
        this.startedAt = startedAt;
        this.expiresAt = expiresAt;
        this.members = new LinkedList<>(members);
    }

    public Optional<Holder<RealmInstance>> definition() {
        return Optional.ofNullable(this.definition);
    }

    public long startedAt() {
        return this.startedAt;
    }

    public long expiresAt() {
        return this.expiresAt;
    }

    public List<UUID> members() {
        return this.members;
    }

    public boolean active() {
        return this.definition != null;
    }

    public void start(Holder<RealmInstance> definition, long gameTime, long duration) {
        this.definition = definition;
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
