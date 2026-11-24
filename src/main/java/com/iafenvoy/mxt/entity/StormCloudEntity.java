package com.iafenvoy.mxt.entity;

import com.iafenvoy.mxt.data.StormCloudOptions;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtEntityDataSerializers;
import com.iafenvoy.mxt.registry.MxtEntityTypes;
import com.iafenvoy.mxt.util.EntityReferences;
import com.iafenvoy.mxt.util.MxtCodecs;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class StormCloudEntity extends Entity {
    public static final int TRACKING_CHUNKS = 24;
    public static final int RENDER_DISTANCE = TRACKING_CHUNKS * 16;
    private static final double FOLLOW_FRACTION = 0.12;
    private static final double MAX_FOLLOW_STEP = 0.30;
    private static final double ARRIVAL_DISTANCE = 0.01;
    private static final long TIMED = -2L, TRIBULATION_ACTIVE = -1L;
    private static final int DISSOLVE_TICKS = 23;
    private static final Codec<EntityReference<Entity>> OWNER_CODEC = MxtCodecs.entityReferenceWithLegacyString();

    private static final EntityDataAccessor<StormCloudOptions> OPTIONS =
            SynchedEntityData.defineId(StormCloudEntity.class, MxtEntityDataSerializers.STORM_CLOUD_OPTIONS.get());
    private static final EntityDataAccessor<Long> CREATED_AT =
            SynchedEntityData.defineId(StormCloudEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DISSOLVE_AT =
            SynchedEntityData.defineId(StormCloudEntity.class, EntityDataSerializers.LONG);

    private final InterpolationHandler interpolation = new InterpolationHandler(this);
    private @Nullable EntityReference<Entity> owner;

    public StormCloudEntity(EntityType<? extends StormCloudEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        if (!level.isClientSide()) this.entityData.set(CREATED_AT, level.getGameTime());
    }

    public static @Nullable StormCloudEntity create(ServerLevel level, Vec3 position, StormCloudOptions options, @Nullable Entity owner) {
        if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
                || !Level.isInSpawnableBounds(BlockPos.containing(position))) return null;
        StormCloudEntity cloud = MxtEntityTypes.STORM_CLOUD.get().create(level, EntitySpawnReason.TRIGGERED);
        if (cloud == null) return null;
        cloud.setPos(position);
        cloud.setOptions(options);
        cloud.setOwner(owner);
        return cloud;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NonNull Builder builder) {
        builder.define(OPTIONS, StormCloudOptions.DEFAULT);
        builder.define(CREATED_AT, -1L);
        builder.define(DISSOLVE_AT, TIMED);
    }

    public StormCloudOptions options() {
        return this.entityData.get(OPTIONS);
    }

    public void setOptions(StormCloudOptions options) {
        this.entityData.set(OPTIONS, options);
    }

    public void setOwner(@Nullable Entity owner) {
        this.owner = owner == null ? null : EntityReference.of(owner.getUUID());
    }

    public long ageTicks() {
        long createdAt = this.entityData.get(CREATED_AT);
        return createdAt < 0 ? 0 : Math.max(0, this.level().getGameTime() - createdAt);
    }

    public float visualAgeTicks(float partialTick) {
        float age = this.ageTicks();
        // A frozen DeltaTracker can return 1, which must not trigger the next tick's roll.
        return Math.min(age + partialTick, Math.nextDown(age + 1.0F));
    }

    public void bindToTribulation() {
        this.entityData.set(DISSOLVE_AT, TRIBULATION_ACTIVE);
    }

    public boolean isActiveTribulationCloudFor(Entity owner) {
        return this.entityData.get(DISSOLVE_AT) == TRIBULATION_ACTIVE && this.owner != null && this.owner.matches(owner);
    }

    public void dissolve() {
        if (this.entityData.get(DISSOLVE_AT) == TRIBULATION_ACTIVE)
            this.entityData.set(DISSOLVE_AT, this.level().getGameTime());
    }

    public boolean expired() {
        long dissolveAt = this.entityData.get(DISSOLVE_AT);
        if (dissolveAt == TIMED) return this.ageTicks() >= this.options().lifetime();
        return dissolveAt >= 0 && this.level().getGameTime() - dissolveAt >= DISSOLVE_TICKS;
    }

    public float tribulationOpacity(float partialTick) {
        long dissolveAt = this.entityData.get(DISSOLVE_AT);
        if (dissolveAt == TIMED) return 1.0F;
        double age = this.ageTicks() + (double) partialTick;
        double fade = 1.0D;
        if (dissolveAt >= 0) {
            double elapsed = Math.max(0.0D, this.level().getGameTime() - dissolveAt + (double) partialTick);
            age -= elapsed;
            fade = 1.0D - Mth.smoothstep(Math.min(1.0D, elapsed / DISSOLVE_TICKS));
        }
        return (float) (fade * Mth.smoothstep(Math.clamp(age / 13.0D, 0.0D, 1.0D)));
    }

    public boolean isTribulationCloud() {
        return this.entityData.get(DISSOLVE_AT) != TIMED;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.interpolation.interpolate();
            return;
        }
        if (this.expired()) {
            this.discard();
            return;
        }
        long dissolveAt = this.entityData.get(DISSOLVE_AT);
        if (dissolveAt >= 0) return;
        Entity target = EntityReferences.inLevel(this.owner, this.level());
        if (dissolveAt == TRIBULATION_ACTIVE && (!(target instanceof LivingEntity living) || !living.isAlive()
                || living.getExistingData(MxtAttachments.TRIBULATION)
                .filter(run -> run.tribulation().isPresent() && run.stormCloud().filter(this.getUUID()::equals).isPresent()).isEmpty())) {
            this.dissolve();
            return;
        }
        if (target == null || target == this || !target.isAlive()) return;

        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double distance = Math.hypot(dx, dz);
        if (distance == 0.0) return;
        if (distance <= ARRIVAL_DISTANCE) {
            this.setPos(target.getX(), this.getY(), target.getZ());
            return;
        }
        double fraction = Math.min(FOLLOW_FRACTION, MAX_FOLLOW_STEP / distance);
        this.setPos(this.getX() + dx * fraction, this.getY(), this.getZ() + dz * fraction);
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return this.interpolation;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSquared) {
        // The world effect renderer tests the full cloud volume instead of the entity section.
        return false;
    }

    @Override
    public boolean hurtServer(@NonNull ServerLevel level, @NonNull DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        this.setOptions(input.read("cloud", StormCloudOptions.CODEC).orElse(StormCloudOptions.DEFAULT));
        this.owner = input.read("owner", OWNER_CODEC).orElse(null);
        long now = this.level().getGameTime();
        this.entityData.set(CREATED_AT, Math.clamp(input.getLongOr("cloud_created_at", now), 0, now));
        this.entityData.set(DISSOLVE_AT, Math.clamp(input.getLongOr("cloud_dissolve_at", TIMED), TIMED, now));
        this.setNoGravity(true);
    }

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        output.store("cloud", StormCloudOptions.CODEC, this.options());
        EntityReference.store(this.owner, output, "owner");
        output.putLong("cloud_created_at", this.entityData.get(CREATED_AT));
        if (this.isTribulationCloud()) output.putLong("cloud_dissolve_at", this.entityData.get(DISSOLVE_AT));
    }
}
