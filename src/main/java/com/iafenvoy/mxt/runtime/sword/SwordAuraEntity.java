package com.iafenvoy.mxt.runtime.sword;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.registry.MxtEntityTypes;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Visual sword aura projectile. Movement is server-owned; the client only renders the synchronized state.
 */
public final class SwordAuraEntity extends Entity implements OwnableEntity {
    public static final int DEFAULT_BLADE_COLOR = 0xC778D9FF, DEFAULT_AURA_COLOR = 0xCC78D9FF;
    public static final boolean DEFAULT_RADIAL_FLAME = false;
    public static final float DEFAULT_LENGTH = 1.65F;
    public static final float DEFAULT_BLADE_WIDTH = 0.26F;
    public static final float DEFAULT_THICKNESS = 0.08F;
    public static final float DEFAULT_HANDLE_LENGTH = 0.44F;
    public static final float DEFAULT_GUARD_WIDTH = 0.52F;
    public static final float DEFAULT_SCALE = 1.0F;
    public static final int DEFAULT_LIFETIME_TICKS = 80;
    private int lifetime = DEFAULT_LIFETIME_TICKS;
    // Impact behavior and who it is attributed to: server-only data, so neither is synched and a client learns
    // neither. No action keeps the aura phasing through the world, which is what it did before the field existed.
    private @Nullable EntityAction collideAction;
    private @Nullable EntityReference<LivingEntity> owner;

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
        this.setNoGravity(true);
        this.noPhysics = true;
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

    public void setLifetime(int ticks) {
        if (ticks <= 0) throw new IllegalArgumentException("Sword aura lifetime must be positive");
        this.lifetime = Math.min(ticks, 72000);
    }

    // The two travel together: an impact with nobody to attribute it to could only run the action against the aura
    // itself, which is not the entity the action is about.
    public void setCollideAction(LivingEntity caster, EntityAction action) {
        this.owner = EntityReference.of(caster);
        this.collideAction = action;
    }

    @Override
    public @Nullable EntityReference<LivingEntity> getOwnerReference() {
        return this.owner;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSquared) {
        // A visual projectile must not inherit the tiny collision box's default render distance.
        return distanceSquared < 256.0 * 256.0;
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 velocity = this.getDeltaMovement();
        if (!this.level().isClientSide()) {
            if (this.tickCount >= this.lifetime) {
                this.discard();
                return;
            }
            if (velocity.lengthSqr() > 1.0E-8D) {
                if (this.impact(velocity)) return;
                this.move(MoverType.SELF, velocity);
                this.updateRotation(velocity);
            }
        }
    }

    // The aura collides with nothing (noPhysics), so a move leaves no collision flag behind and the block this
    // tick's movement would enter has to be looked for by hand.
    private boolean impact(Vec3 velocity) {
        if (this.collideAction == null) return false;
        Vec3 from = this.position();
        Vec3 hit = this.blockHit(from, velocity);
        if (hit == null) return false;
        LivingEntity caster = EntityReference.getLivingEntity(this.owner, this.level());
        if (caster != null) this.collideAction.execute(new EntityActionContext(caster, FormulaContext.of(caster), hit));
        this.discard();
        return true;
    }

    // The ray gives the exact impact point but only covers the box's centre line, so the volume the box sweeps is
    // asked when it misses: the box decides what the aura can hit, the blade's visual length does not.
    private @Nullable Vec3 blockHit(Vec3 from, Vec3 velocity) {
        BlockHitResult hit = this.level().clip(new ClipContext(from, from.add(velocity), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() != HitResult.Type.MISS) return hit.getLocation();
        return this.level().getBlockCollisions(this, this.getBoundingBox().expandTowards(velocity)).iterator().hasNext() ? from : null;
    }

    private void updateRotation(Vec3 velocity) {
        if (velocity.lengthSqr() <= 1.0E-8D) return;
        Vec3 direction = velocity.normalize();
        this.setYRot((float) (Math.atan2(-direction.x, direction.z) * 180.0D / Math.PI));
        this.setXRot((float) (Math.asin(-direction.y) * 180.0D / Math.PI));
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        this.setLifetime(input.getIntOr("lifetime", DEFAULT_LIFETIME_TICKS));
        this.setBladeColor(input.getIntOr("blade_color", DEFAULT_BLADE_COLOR));
        this.setAuraColor(input.getIntOr("aura_color", DEFAULT_AURA_COLOR));
        this.setRadialFlame(input.getBooleanOr("radial_flame", DEFAULT_RADIAL_FLAME));
        this.setLength(input.getFloatOr("length", DEFAULT_LENGTH));
        this.setBladeWidth(input.getFloatOr("blade_width", DEFAULT_BLADE_WIDTH));
        this.setThickness(input.getFloatOr("thickness", DEFAULT_THICKNESS));
        this.setHandleLength(input.getFloatOr("handle_length", DEFAULT_HANDLE_LENGTH));
        this.setGuardWidth(input.getFloatOr("guard_width", DEFAULT_GUARD_WIDTH));
        this.setScale(input.getFloatOr("scale", DEFAULT_SCALE));
        this.owner = EntityReference.read(input, "caster");
        this.collideAction = input.read("collide_action", EntityAction.CODEC).orElse(null);
    }

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        output.putInt("lifetime", this.lifetime);
        output.putInt("blade_color", this.getBladeColor());
        output.putInt("aura_color", this.getAuraColor());
        output.putBoolean("radial_flame", this.isRadialFlame());
        output.putFloat("length", this.getLength());
        output.putFloat("blade_width", this.getBladeWidth());
        output.putFloat("thickness", this.getThickness());
        output.putFloat("handle_length", this.getHandleLength());
        output.putFloat("guard_width", this.getGuardWidth());
        output.putFloat("scale", this.getScale());
        if (this.collideAction != null) {
            EntityReference.store(this.owner, output, "caster");
            output.store("collide_action", EntityAction.CODEC, this.collideAction);
        }
    }

    private static float dimension(float value, String name) {
        if (!Float.isFinite(value) || value <= 0.0F)
            throw new IllegalArgumentException("Sword aura " + name + " must be finite and positive");
        return Math.clamp(value, 0.01F, 32.0F);
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
