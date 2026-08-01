package com.iafenvoy.mxt.runtime.world;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Persisted, invulnerable manifestation of a transferred soul. Rendering is intentionally content/client-owned.
 */
public final class SoulEntity extends Entity {
    private UUID origin;
    private long createdAt;
    private String source = "";

    public SoulEntity(EntityType<? extends SoulEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public void bind(UUID origin, long createdAt, String source) {
        this.origin = origin;
        this.createdAt = createdAt;
        this.source = source == null ? "" : source;
    }

    public Optional<UUID> origin() {
        return Optional.ofNullable(this.origin);
    }

    public long createdAt() {
        return this.createdAt;
    }

    public String source() {
        return this.source;
    }

    @Override
    protected void defineSynchedData(@NonNull Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.origin = input.read("origin", UUIDUtil.CODEC).orElse(null);
        this.createdAt = input.getLongOr("created_at", -1L);
        this.source = input.getStringOr("source", "");
    }

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        if (this.origin != null) output.store("origin", UUIDUtil.CODEC, this.origin);
        output.putLong("created_at", this.createdAt);
        output.putString("source", this.source);
    }

    @Override
    public boolean hurtServer(@NonNull ServerLevel level, @NonNull DamageSource source, float amount) {
        return false;
    }
}
