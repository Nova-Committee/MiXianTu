package com.iafenvoy.mxt.runtime.projectile;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
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

// Movement and optional block impacts are server-owned; rendering never advances the projectile.
public abstract class VisualProjectileEntity extends Entity implements OwnableEntity {
    public static final int DEFAULT_LIFETIME_TICKS = 80;
    private int lifetime = DEFAULT_LIFETIME_TICKS;
    private @Nullable EntityAction collideAction;
    private @Nullable EntityReference<LivingEntity> owner;

    protected VisualProjectileEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    public void setLifetime(int ticks) {
        if (ticks <= 0) throw new IllegalArgumentException("Projectile lifetime must be positive");
        this.lifetime = Math.min(ticks, 72000);
    }

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
        return distanceSquared < 256.0 * 256.0;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) return;
        if (this.tickCount >= this.lifetime) {
            this.discard();
            return;
        }
        Vec3 velocity = this.getDeltaMovement();
        if (velocity.lengthSqr() <= 1.0E-8D || this.impact(velocity)) return;
        this.move(MoverType.SELF, velocity);
        this.updateRotation(velocity);
    }

    private boolean impact(Vec3 velocity) {
        if (this.collideAction == null) return false;
        Vec3 from = this.position();
        BlockHitResult result = this.level().clip(new ClipContext(from, from.add(velocity),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 hit;
        if (result.getType() != HitResult.Type.MISS) hit = result.getLocation();
        // noPhysics leaves no collision flag, so also check the volume swept by the collision box.
        else if (this.level().getBlockCollisions(this, this.getBoundingBox().expandTowards(velocity)).iterator().hasNext()) hit = from;
        else return false;
        LivingEntity caster = EntityReference.getLivingEntity(this.owner, this.level());
        if (caster != null) this.collideAction.execute(new EntityActionContext(caster, FormulaContext.of(caster), hit));
        this.discard();
        return true;
    }

    protected final void updateRotation(Vec3 velocity) {
        if (velocity.lengthSqr() <= 1.0E-8D) return;
        Vec3 direction = velocity.normalize();
        this.setYRot((float) (Math.atan2(-direction.x, direction.z) * 180.0D / Math.PI));
        this.setXRot((float) (Math.asin(-direction.y) * 180.0D / Math.PI));
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        this.setLifetime(input.getIntOr("lifetime", DEFAULT_LIFETIME_TICKS));
        this.owner = EntityReference.read(input, "caster");
        this.collideAction = input.read("collide_action", EntityAction.CODEC).orElse(null);
    }

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        output.putInt("lifetime", this.lifetime);
        if (this.collideAction != null) {
            EntityReference.store(this.owner, output, "caster");
            output.store("collide_action", EntityAction.CODEC, this.collideAction);
        }
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
