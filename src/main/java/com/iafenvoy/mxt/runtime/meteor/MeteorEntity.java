package com.iafenvoy.mxt.runtime.meteor;

import com.iafenvoy.mxt.registry.MxtEntityTypes;
import com.iafenvoy.mxt.runtime.projectile.VisualProjectileEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

public final class MeteorEntity extends VisualProjectileEntity {
    public static final int DEFAULT_COLOR = 0xE6FF8A32;
    public static final float DEFAULT_RADIUS = 0.55F;
    public static final float DEFAULT_TAIL_LENGTH = 3.0F;
    public static final float DEFAULT_SCALE = 1.0F;
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TAIL_LENGTH = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.FLOAT);

    public MeteorEntity(EntityType<? extends MeteorEntity> type, Level level) {
        super(type, level);
    }

    public MeteorEntity(Level level, Vec3 position, Vec3 velocity) {
        this(MxtEntityTypes.METEOR.get(), level);
        this.setPos(position);
        this.setDeltaMovement(velocity);
        this.updateRotation(velocity);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NonNull Builder builder) {
        builder.define(COLOR, DEFAULT_COLOR);
        builder.define(RADIUS, DEFAULT_RADIUS);
        builder.define(TAIL_LENGTH, DEFAULT_TAIL_LENGTH);
        builder.define(SCALE, DEFAULT_SCALE);
    }

    public int getColor() {
        return this.getEntityData().get(COLOR);
    }

    public void setColor(int color) {
        this.getEntityData().set(COLOR, color);
    }

    public float getRadius() {
        return this.getEntityData().get(RADIUS);
    }

    public void setRadius(float radius) {
        this.getEntityData().set(RADIUS, dimension(radius));
    }

    public float getTailLength() {
        return this.getEntityData().get(TAIL_LENGTH);
    }

    public void setTailLength(float length) {
        this.getEntityData().set(TAIL_LENGTH, dimension(length));
    }

    public float getScale() {
        return this.getEntityData().get(SCALE);
    }

    public void setScale(float scale) {
        this.getEntityData().set(SCALE, dimension(scale));
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setColor(input.getIntOr("color", DEFAULT_COLOR));
        this.setRadius(input.getFloatOr("radius", DEFAULT_RADIUS));
        this.setTailLength(input.getFloatOr("tail_length", DEFAULT_TAIL_LENGTH));
        this.setScale(input.getFloatOr("scale", DEFAULT_SCALE));
    }

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("color", this.getColor());
        output.putFloat("radius", this.getRadius());
        output.putFloat("tail_length", this.getTailLength());
        output.putFloat("scale", this.getScale());
    }

    private static float dimension(float value) {
        if (!Float.isFinite(value) || value <= 0.0F)
            throw new IllegalArgumentException("Meteor dimensions must be finite and positive");
        return Math.clamp(value, 0.01F, 32.0F);
    }
}
