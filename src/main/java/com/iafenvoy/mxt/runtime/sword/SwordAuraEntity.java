package com.iafenvoy.mxt.runtime.sword;

import com.iafenvoy.mxt.registry.MxtEntityTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/** Visual sword aura projectile. Movement is server-owned; the client only renders the synchronized state. */
public final class SwordAuraEntity extends Entity {
    public static final int DEFAULT_COLOR = 0x78D9FF;
    public static final float DEFAULT_BLADE_ALPHA = 0.78F;
    public static final float DEFAULT_AURA_ALPHA = 0.80F;
    public static final float DEFAULT_LENGTH = 1.65F;
    public static final float DEFAULT_BLADE_WIDTH = 0.26F;
    public static final float DEFAULT_THICKNESS = 0.08F;
    public static final float DEFAULT_HANDLE_LENGTH = 0.44F;
    public static final float DEFAULT_GUARD_WIDTH = 0.52F;
    public static final float DEFAULT_SCALE = 1.0F;
    public static final int MAX_LIFETIME_TICKS = 80;

    private static final EntityDataAccessor<Integer> COLOR =
            SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> BLADE_ALPHA =
            SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> AURA_ALPHA =
            SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LENGTH =
            SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BLADE_WIDTH =
            SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> THICKNESS =
            SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HANDLE_LENGTH =
            SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> GUARD_WIDTH =
            SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SCALE =
            SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.FLOAT);

    public SwordAuraEntity(EntityType<? extends SwordAuraEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    public SwordAuraEntity(Level level, Vec3 position, Vec3 velocity, int color, float bladeAlpha,
                           float auraAlpha) {
        this(MxtEntityTypes.SWORD_AURA.get(), level);
        this.setPos(position);
        this.setColor(color);
        this.setBladeAlpha(bladeAlpha);
        this.setAuraAlpha(auraAlpha);
        this.setDeltaMovement(velocity);
        updateRotation(velocity);
    }

    @Override
    protected void defineSynchedData(@NonNull Builder builder) {
        builder.define(COLOR, DEFAULT_COLOR);
        builder.define(BLADE_ALPHA, DEFAULT_BLADE_ALPHA);
        builder.define(AURA_ALPHA, DEFAULT_AURA_ALPHA);
        builder.define(LENGTH, DEFAULT_LENGTH);
        builder.define(BLADE_WIDTH, DEFAULT_BLADE_WIDTH);
        builder.define(THICKNESS, DEFAULT_THICKNESS);
        builder.define(HANDLE_LENGTH, DEFAULT_HANDLE_LENGTH);
        builder.define(GUARD_WIDTH, DEFAULT_GUARD_WIDTH);
        builder.define(SCALE, DEFAULT_SCALE);
    }

    public int color() {
        return this.getEntityData().get(COLOR);
    }

    public void setColor(int color) {
        if (color < 0 || color > 0xFFFFFF) throw new IllegalArgumentException("Sword aura colour must be an RGB value");
        this.getEntityData().set(COLOR, color);
    }

    public float bladeAlpha() {
        return this.getEntityData().get(BLADE_ALPHA);
    }

    public void setBladeAlpha(float alpha) {
        this.getEntityData().set(BLADE_ALPHA, alphaValue(alpha, "blade alpha"));
    }

    public float auraAlpha() {
        return this.getEntityData().get(AURA_ALPHA);
    }

    public void setAuraAlpha(float alpha) {
        this.getEntityData().set(AURA_ALPHA, alphaValue(alpha, "aura alpha"));
    }

    public float length() {
        return this.getEntityData().get(LENGTH);
    }

    public void setLength(float length) {
        this.getEntityData().set(LENGTH, dimension(length, "length"));
    }

    public float bladeWidth() {
        return this.getEntityData().get(BLADE_WIDTH);
    }

    public void setBladeWidth(float bladeWidth) {
        this.getEntityData().set(BLADE_WIDTH, dimension(bladeWidth, "blade width"));
    }

    public float thickness() {
        return this.getEntityData().get(THICKNESS);
    }

    public void setThickness(float thickness) {
        this.getEntityData().set(THICKNESS, dimension(thickness, "thickness"));
    }

    public float handleLength() {
        return this.getEntityData().get(HANDLE_LENGTH);
    }

    public void setHandleLength(float handleLength) {
        this.getEntityData().set(HANDLE_LENGTH, dimension(handleLength, "handle length"));
    }

    public float guardWidth() {
        return this.getEntityData().get(GUARD_WIDTH);
    }

    public void setGuardWidth(float guardWidth) {
        this.getEntityData().set(GUARD_WIDTH, dimension(guardWidth, "guard width"));
    }

    public float scale() {
        return this.getEntityData().get(SCALE);
    }

    public void setScale(float scale) {
        this.getEntityData().set(SCALE, dimension(scale, "scale"));
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 velocity = this.getDeltaMovement();
        if (!this.level().isClientSide()) {
            if (this.tickCount >= MAX_LIFETIME_TICKS) {
                this.discard();
                return;
            }
            if (velocity.lengthSqr() > 1.0E-8D) {
                this.move(MoverType.SELF, velocity);
                updateRotation(velocity);
            }
        }
    }

    private void updateRotation(Vec3 velocity) {
        if (velocity.lengthSqr() <= 1.0E-8D) return;
        Vec3 direction = velocity.normalize();
        this.setYRot((float) (Math.atan2(-direction.x, direction.z) * 180.0D / Math.PI));
        this.setXRot((float) (Math.asin(-direction.y) * 180.0D / Math.PI));
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        this.setColor(input.getIntOr("color", DEFAULT_COLOR));
        this.setBladeAlpha(input.getFloatOr("blade_alpha", DEFAULT_BLADE_ALPHA));
        this.setAuraAlpha(input.getFloatOr("aura_alpha", DEFAULT_AURA_ALPHA));
        this.setLength(input.getFloatOr("length", DEFAULT_LENGTH));
        this.setBladeWidth(input.getFloatOr("blade_width", DEFAULT_BLADE_WIDTH));
        this.setThickness(input.getFloatOr("thickness", DEFAULT_THICKNESS));
        this.setHandleLength(input.getFloatOr("handle_length", DEFAULT_HANDLE_LENGTH));
        this.setGuardWidth(input.getFloatOr("guard_width", DEFAULT_GUARD_WIDTH));
        this.setScale(input.getFloatOr("scale", DEFAULT_SCALE));
    }

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        output.putInt("color", this.color());
        output.putFloat("blade_alpha", this.bladeAlpha());
        output.putFloat("aura_alpha", this.auraAlpha());
        output.putFloat("length", this.length());
        output.putFloat("blade_width", this.bladeWidth());
        output.putFloat("thickness", this.thickness());
        output.putFloat("handle_length", this.handleLength());
        output.putFloat("guard_width", this.guardWidth());
        output.putFloat("scale", this.scale());
    }

    private static float dimension(float value, String name) {
        if (!Float.isFinite(value) || value <= 0.0F)
            throw new IllegalArgumentException("Sword aura " + name + " must be finite and positive");
        return Math.clamp(value, 0.01F, 32.0F);
    }

    private static float alphaValue(float value, String name) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Sword aura " + name + " must be finite");
        return Math.clamp(value, 0.0F, 1.0F);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurtServer(@NonNull ServerLevel level, @NonNull DamageSource source, float amount) {
        return false;
    }
}
