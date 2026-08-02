package com.iafenvoy.mxt.runtime.artifact;

import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Transient, server-authoritative vehicle used by the generic flying-sword controller.
 */
public final class FlyingSwordEntity extends Entity {
    private double speed = 0.05D;

    public FlyingSwordEntity(EntityType<? extends FlyingSwordEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public void setFlightSpeed(double speed) {
        this.speed = Math.clamp(speed, 0.01D, 1.0D);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) return;
        if (!(this.getFirstPassenger() instanceof ServerPlayer rider) || !rider.isAlive()) {
            this.discard();
            return;
        }
        Input input = rider.getLastClientInput();
        Vec3 horizontal = rider.getLastClientMoveIntent();
        double vertical = input.jump() == input.shift() ? 0.0D : input.jump() ? 1.0D : -1.0D;
        double multiplier = input.sprint() ? 1.5D : 1.0D;
        Vec3 movement = new Vec3(horizontal.x * this.speed * multiplier, vertical * this.speed, horizontal.z * this.speed * multiplier);
        if (movement.lengthSqr() > 0.0D && this.level().noCollision(this, this.getBoundingBox().move(movement))) {
            this.move(MoverType.SELF, movement);
            this.setDeltaMovement(movement);
        } else {
            this.setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override
    protected void defineSynchedData(@NonNull Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
    }

    @Override
    public boolean hurtServer(@NonNull ServerLevel level, @NonNull DamageSource source, float amount) {
        return false;
    }
}
