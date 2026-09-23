package com.iafenvoy.mxt.runtime.artifact;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Transient, server-authoritative vehicle used by the generic flying-sword controller.
 */
public final class FlyingSwordEntity extends Entity {
    // The stack the clients draw as this mount; empty renders nothing, which is what a vehicle that was summoned
    // without a carry looks like. The vehicle keeps the stack it is handed, so callers pass a copy.
    private static final EntityDataAccessor<ItemStack> DATA_VISUAL = SynchedEntityData.defineId(FlyingSwordEntity.class, EntityDataSerializers.ITEM_STACK);
    // Only part of the rider's pitch is followed, so looking straight down does not stand the mount on its end.
    private static final float PITCH_RATIO = 0.5F;
    // The server moves this in whole ticks and a client draws twenty of those a second, so a mount carrying the player
    // would visibly step. Smoothing between the packets is what the vanilla vehicles do for the same reason.
    private final InterpolationHandler interpolation = new InterpolationHandler(this);
    private double speed = 0.05D;

    public FlyingSwordEntity(EntityType<? extends FlyingSwordEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public void setVisual(ItemStack visual) {
        this.entityData.set(DATA_VISUAL, visual);
    }

    public ItemStack visual() {
        return this.entityData.get(DATA_VISUAL);
    }

    // Clamped to a usable range, so a pack formula cannot make the sword unmovable or uncontrollable.
    public void setFlightSpeed(double speed) {
        this.speed = Math.clamp(speed, 0.01D, 1.0D);
    }

    @Override
    public void tick() {
        super.tick();
        this.interpolation.interpolate();
        if (this.level().isClientSide()) return;
        if (!(this.getFirstPassenger() instanceof ServerPlayer rider) || !rider.isAlive()) {
            this.discard();
            return;
        }
        Input input = rider.getLastClientInput();
        Vec3 horizontal = rider.getLastClientMoveIntent();
        // The mount faces where its rider looks, which is what turns the blade underfoot; the server owns the value
        // and the ordinary entity rotation packets carry it to the clients.
        this.setYRot(rider.getYRot());
        this.setXRot(rider.getXRot() * PITCH_RATIO);
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

    // NeoForge poses the rider sitting by default; a flying sword is stood on.
    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return this.interpolation;
    }

    @Override
    protected void defineSynchedData(@NonNull Builder builder) {
        builder.define(DATA_VISUAL, ItemStack.EMPTY);
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        this.setVisual(input.read("visual", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
    }

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        ItemStack visual = this.visual();
        if (!visual.isEmpty()) output.store("visual", ItemStack.OPTIONAL_CODEC, visual);
    }

    @Override
    public boolean hurtServer(@NonNull ServerLevel level, @NonNull DamageSource source, float amount) {
        return false;
    }
}
