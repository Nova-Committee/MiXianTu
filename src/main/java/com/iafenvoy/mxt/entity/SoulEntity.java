package com.iafenvoy.mxt.entity;

import com.iafenvoy.mxt.registry.MxtEntityDataSerializers;
import com.iafenvoy.mxt.runtime.world.SoulAppearance;
import com.iafenvoy.mxt.runtime.world.SoulService;
import com.iafenvoy.mxt.runtime.world.SoulSnapshot;
import com.mojang.logging.LogUtils;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;

// The source's complete save is inert; only the separate appearance is synchronized to tracking clients.
public final class SoulEntity extends Entity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final EntityDataAccessor<Optional<SoulAppearance>> APPEARANCE = SynchedEntityData.defineId(SoulEntity.class, MxtEntityDataSerializers.SOUL_APPEARANCE.get());
    private UUID origin;
    private long createdAt = -1;
    private String source = "";
    private @Nullable SoulSnapshot snapshot;
    private @Nullable Object clientAppearance;

    public SoulEntity(EntityType<? extends SoulEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.setInvulnerable(true);
    }

    public void bind(UUID origin, long createdAt, String source) {
        this.origin = origin;
        this.createdAt = createdAt;
        this.source = source == null ? "" : source;
    }

    public void capture(LivingEntity origin, long createdAt, String source) {
        this.snapshot = SoulSnapshot.capture(origin);
        this.bind(origin.getUUID(), createdAt, source);
        this.getEntityData().set(APPEARANCE, Optional.of(SoulAppearance.capture(origin, this.snapshot)));
        this.setPos(origin.position());
        this.setYRot(origin.getYRot());
        this.setXRot(origin.getXRot());
        this.setOldPosAndRot();
    }

    public Optional<SoulSnapshot> snapshot() {
        return Optional.ofNullable(this.snapshot);
    }

    public Optional<SoulAppearance> appearance() {
        return this.getEntityData().get(APPEARANCE);
    }

    // Client-owned cache on the entity avoids a global map retaining worlds after unload.
    public @Nullable Object clientAppearance() {
        return this.clientAppearance;
    }

    public void clientAppearance(@Nullable Object appearance) {
        this.clientAppearance = appearance;
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
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
            if (this.createdAt < 0) this.createdAt = level.getServer().overworld().getGameTime();
            if (SoulService.expired(level, this.createdAt)) SoulService.expire(this);
        }
    }

    @Override
    protected void defineSynchedData(@NonNull Builder builder) {
        builder.define(APPEARANCE, Optional.empty());
    }

    @Override
    public void onSyncedDataUpdated(@NonNull EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (APPEARANCE.equals(accessor)) {
            this.clientAppearance = null;
            this.refreshDimensions();
        }
    }

    @Override
    public @NonNull EntityDimensions getDimensions(@NonNull Pose pose) {
        return this.appearance().map(visual -> EntityDimensions.fixed(visual.width(), visual.height())).orElseGet(() -> super.getDimensions(pose));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.clientAppearance = null;
        this.getEntityData().set(APPEARANCE, Optional.empty());
        this.origin = input.read("origin", UUIDUtil.CODEC).orElse(null);
        this.createdAt = input.getLongOr("created_at", -1L);
        this.source = input.getStringOr("source", "");
        this.snapshot = input.read("snapshot", SoulSnapshot.CODEC).orElse(null);
        if (this.snapshot != null && this.level() instanceof ServerLevel level) {
            SoulAppearance appearance = SoulAppearance.fallback(this.snapshot);
            try {
                appearance = this.snapshot.appearanceSource(level).map(entity -> SoulAppearance.capture(entity, this.snapshot)).orElse(appearance);
            } catch (RuntimeException failure) {
                LOGGER.warn("Unable to rebuild soul appearance for {} ({})", this.origin, this.snapshot.entityType(), failure);
            }
            this.getEntityData().set(APPEARANCE, Optional.of(appearance));
        }
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.noPhysics = true;
        this.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        if (this.origin != null) output.store("origin", UUIDUtil.CODEC, this.origin);
        output.putLong("created_at", this.createdAt);
        output.putString("source", this.source);
        if (this.snapshot != null) output.store("snapshot", SoulSnapshot.CODEC, this.snapshot);
    }

    @Override
    public boolean hurtServer(@NonNull ServerLevel level, @NonNull DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean ignorePassenger) {
        return false;
    }

    @Override
    protected void onBelowWorld() {
    }
}
