package com.iafenvoy.mxt.runtime.sword;

import com.iafenvoy.mxt.registry.MxtEntityTypes;
import com.iafenvoy.mxt.runtime.projectile.VisualProjectileEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Visual sword aura projectile. Movement is server-owned; the client only renders the synchronized state.
 */
public final class SwordAuraEntity extends VisualProjectileEntity {
    public static final int DEFAULT_BLADE_COLOR = 0xC778D9FF, DEFAULT_AURA_COLOR = 0xCC78D9FF;
    public static final boolean DEFAULT_RADIAL_FLAME = false;
    public static final float DEFAULT_LENGTH = 1.65F;
    public static final float DEFAULT_BLADE_WIDTH = 0.26F;
    public static final float DEFAULT_THICKNESS = 0.08F;
    public static final float DEFAULT_HANDLE_LENGTH = 0.44F;
    public static final float DEFAULT_GUARD_WIDTH = 0.52F;
    public static final float DEFAULT_SCALE = 1.0F;
    private static final EntityDataAccessor<Integer> BLADE_COLOR = SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AURA_COLOR = SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RADIAL_FLAME = SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> LENGTH = SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BLADE_WIDTH = SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> THICKNESS = SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HANDLE_LENGTH = SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> GUARD_WIDTH = SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(SwordAuraEntity.class, EntityDataSerializers.FLOAT);

    public SwordAuraEntity(EntityType<? extends SwordAuraEntity> type, Level level) {
        super(type, level);
    }

    public SwordAuraEntity(Level level, Vec3 position, Vec3 velocity) {
        this(MxtEntityTypes.SWORD_AURA.get(), level);
        this.setPos(position);
        this.setDeltaMovement(velocity);
        this.updateRotation(velocity);
    }

    @Override
    protected void defineSynchedData(@NonNull Builder builder) {
        builder.define(BLADE_COLOR, DEFAULT_BLADE_COLOR);
        builder.define(AURA_COLOR, DEFAULT_AURA_COLOR);
        builder.define(RADIAL_FLAME, DEFAULT_RADIAL_FLAME);
        builder.define(LENGTH, DEFAULT_LENGTH);
        builder.define(BLADE_WIDTH, DEFAULT_BLADE_WIDTH);
        builder.define(THICKNESS, DEFAULT_THICKNESS);
        builder.define(HANDLE_LENGTH, DEFAULT_HANDLE_LENGTH);
        builder.define(GUARD_WIDTH, DEFAULT_GUARD_WIDTH);
        builder.define(SCALE, DEFAULT_SCALE);
    }

    public int getBladeColor() {
        return this.getEntityData().get(BLADE_COLOR);
    }

    public void setBladeColor(int color) {
        this.getEntityData().set(BLADE_COLOR, color);
    }

    public int getAuraColor() {
        return this.getEntityData().get(AURA_COLOR);
    }

    public void setAuraColor(int color) {
        this.getEntityData().set(AURA_COLOR, color);
    }

    public boolean isRadialFlame() {
        return this.getEntityData().get(RADIAL_FLAME);
    }

    public void setRadialFlame(boolean radialFlame) {
        this.getEntityData().set(RADIAL_FLAME, radialFlame);
    }

    public float getLength() {
        return this.getEntityData().get(LENGTH);
    }

    public void setLength(float length) {
        this.getEntityData().set(LENGTH, dimension(length, "length"));
    }

    public float getBladeWidth() {
        return this.getEntityData().get(BLADE_WIDTH);
    }

    public void setBladeWidth(float bladeWidth) {
        this.getEntityData().set(BLADE_WIDTH, dimension(bladeWidth, "blade width"));
    }

    public float getThickness() {
        return this.getEntityData().get(THICKNESS);
    }

    public void setThickness(float thickness) {
        this.getEntityData().set(THICKNESS, dimension(thickness, "thickness"));
    }

    public float getHandleLength() {
        return this.getEntityData().get(HANDLE_LENGTH);
    }

    public void setHandleLength(float handleLength) {
        this.getEntityData().set(HANDLE_LENGTH, dimension(handleLength, "handle length"));
    }

    public float getGuardWidth() {
        return this.getEntityData().get(GUARD_WIDTH);
    }

    public void setGuardWidth(float guardWidth) {
        this.getEntityData().set(GUARD_WIDTH, dimension(guardWidth, "guard width"));
    }

    public float getScale() {
        return this.getEntityData().get(SCALE);
    }

    public void setScale(float scale) {
        this.getEntityData().set(SCALE, dimension(scale, "scale"));
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setBladeColor(input.getIntOr("blade_color", DEFAULT_BLADE_COLOR));
        this.setAuraColor(input.getIntOr("aura_color", DEFAULT_AURA_COLOR));
        this.setRadialFlame(input.getBooleanOr("radial_flame", DEFAULT_RADIAL_FLAME));
        this.setLength(input.getFloatOr("length", DEFAULT_LENGTH));
        this.setBladeWidth(input.getFloatOr("blade_width", DEFAULT_BLADE_WIDTH));
        this.setThickness(input.getFloatOr("thickness", DEFAULT_THICKNESS));
        this.setHandleLength(input.getFloatOr("handle_length", DEFAULT_HANDLE_LENGTH));
        this.setGuardWidth(input.getFloatOr("guard_width", DEFAULT_GUARD_WIDTH));
        this.setScale(input.getFloatOr("scale", DEFAULT_SCALE));
    }

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("blade_color", this.getBladeColor());
        output.putInt("aura_color", this.getAuraColor());
        output.putBoolean("radial_flame", this.isRadialFlame());
        output.putFloat("length", this.getLength());
        output.putFloat("blade_width", this.getBladeWidth());
        output.putFloat("thickness", this.getThickness());
        output.putFloat("handle_length", this.getHandleLength());
        output.putFloat("guard_width", this.getGuardWidth());
        output.putFloat("scale", this.getScale());
    }

    private static float dimension(float value, String name) {
        if (!Float.isFinite(value) || value <= 0.0F)
            throw new IllegalArgumentException("Sword aura " + name + " must be finite and positive");
        return Math.clamp(value, 0.01F, 32.0F);
    }
}
