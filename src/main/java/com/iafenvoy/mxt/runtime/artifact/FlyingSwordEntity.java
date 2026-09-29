package com.iafenvoy.mxt.runtime.artifact;

import com.iafenvoy.mxt.api.MountVehicle;
import com.iafenvoy.mxt.attachment.FlightAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.ability.type.MountAbilityType;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.mojang.serialization.Codec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative vehicle of one flight: the artifact its owner gave up for the duration, drawn as itself and
 * carrying up to the declared number of riders.
 *
 * <p>Geometry, pose and seat count come from the mount entry the carried stack declares, resolved on both sides from
 * the same synced stack, so a definition changes them without a field being synced for each.
 */
public final class FlyingSwordEntity extends Entity implements MountVehicle {
    // The stack the clients draw as this mount and the server hands back when the flight ends; empty means the mount
    // was summoned without an artifact, which renders nothing and has nowhere to return anything to.
    private static final EntityDataAccessor<ItemStack> DATA_VISUAL = SynchedEntityData.defineId(FlyingSwordEntity.class, EntityDataSerializers.ITEM_STACK);
    // Only part of the rider's pitch is followed, so looking straight down does not stand the mount on its end.
    private static final float PITCH_RATIO = 0.5F;
    // The box is a thin slab under the rider's feet, so boarding would otherwise be a pixel hunt.
    private static final float PICK_RADIUS = 0.35F;
    // The server moves this in whole ticks and a client draws twenty of those a second, so a mount carrying the player
    // would visibly step. Smoothing between the packets is what the vanilla vehicles do for the same reason.
    private final InterpolationHandler interpolation = new InterpolationHandler(this);
    private double speed = 0.05D;
    private EntityReference<LivingEntity> owner;
    // Null until the carried stack has been read once; the stack is compared again on every use, so both the sync of
    // a new visual and a /reload re-read the definition without a second field being kept in step.
    private ItemStack resolvedVisual;
    private MountAbilityType resolvedMount;

    public FlyingSwordEntity(EntityType<? extends FlyingSwordEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    @Override
    public void setVisual(ItemStack visual) {
        this.entityData.set(DATA_VISUAL, visual);
        this.resolvedVisual = null;
        this.refreshDimensions();
    }

    @Override
    public ItemStack visual() {
        return this.entityData.get(DATA_VISUAL);
    }

    @Override
    public void setOwner(UUID owner) {
        this.owner = owner == null ? null : EntityReference.of(owner);
    }

    // Reading the owner back is OwnableEntity's own getOwner(); keeping the reference is all this side does.
    @Nullable
    @Override
    public EntityReference<LivingEntity> getOwnerReference() {
        return this.owner;
    }

    // Clamped to a usable range, so a pack formula cannot make the sword unmovable or uncontrollable.
    @Override
    public void setFlightSpeed(double speed) {
        this.speed = Math.clamp(speed, 0.01D, 1.0D);
    }

    @Override
    public void tick() {
        super.tick();
        // The clients are the side that has to apply what they are sent here: the server moves this itself, so a
        // client that skipped this would draw the mount where it spawned and never anywhere else.
        this.interpolation.interpolate();
        if (this.level().isClientSide()) return;
        // The flight belongs to the player who started it: seat 0 is the driver, so a driver who left (logged out,
        // died, was ejected) must not hand that seat - and the fuel bill - to whoever is still sitting behind them.
        if (this.owner == null || !(this.getFirstPassenger() instanceof ServerPlayer rider)
                || !this.owner.equals(rider.getUUID()) || !rider.isAlive()) {
            this.discard();
            return;
        }
        Input input = rider.getLastClientInput();
        // The descend key is not a vanilla input, so it arrives on its own payload and is kept on the flight record
        // rather than read out of the input packet.
        FlightAttachment data = rider.getExistingData(MxtAttachments.FLIGHT).orElse(null);
        boolean descend = data != null && data.descend();
        Vec3 intent = rider.getLastClientMoveIntent();
        // The mount faces where its rider looks, which is what turns the blade underfoot; the server owns the value
        // and the ordinary entity rotation packets carry it to the clients.
        this.setYRot(rider.getYRot());
        this.setXRot(rider.getXRot() * PITCH_RATIO);
        double vertical = input.jump() == descend ? 0.0D : input.jump() ? 1.0D : -1.0D;
        double multiplier = input.sprint() ? 1.5D : 1.0D;
        Vec3 direction = MxtServerConfig.INSTANCE.flight.followLook.getValue() ? alongLook(rider, intent) : intent;
        Vec3 movement = direction.scale(this.speed * multiplier);
        // Jump and the descend key name a direction outright, so they replace the part of the movement the look
        // would have decided instead of adding to it.
        if (vertical != 0.0D) movement = new Vec3(movement.x, vertical * this.speed, movement.z);
        if (movement.lengthSqr() <= 0.0D) {
            this.setDeltaMovement(Vec3.ZERO);
            this.emitTrail(false);
            return;
        }
        // Every tick really moves, because the flags it leaves behind are what ends a flight that hit something: a
        // move that is only predicted reports nothing, so the collision check downstream could never fire.
        this.move(MoverType.SELF, movement);
        boolean collided = this.horizontalCollision || this.verticalCollision;
        this.setDeltaMovement(collided ? Vec3.ZERO : movement);
        this.emitTrail(!collided);
    }

    // The trail comes off the mount itself rather than off the rider, and a mount that is standing still counts as
    // not moving: `moving_only` is what turns a trail into an aura when the author wants the other one.
    private void emitTrail(boolean moving) {
        MountAbilityType definition = this.mount();
        MountAbilityType.MountTrail trail = definition == null ? null : definition.trail().orElse(null);
        if (trail == null || this.tickCount % trail.interval() != 0) return;
        if (trail.movingOnly() && !moving) return;
        trail.emit(this);
    }

    // Takes the rider's intent apart in the frame it was built in (yaw-relative) and puts it back together around
    // the look, so forward climbs and dives with the head while strafing stays level.
    private static Vec3 alongLook(LivingEntity rider, Vec3 intent) {
        Vec3 forward = getInputVector(new Vec3(0.0D, 0.0D, 1.0D), 1.0F, rider.getYRot());
        Vec3 right = getInputVector(new Vec3(-1.0D, 0.0D, 0.0D), 1.0F, rider.getYRot());
        return rider.getLookAngle().scale(intent.dot(forward)).add(right.scale(intent.dot(right)));
    }

    // The movement belongs to the server, so the client must not answer for it: the default authority would make it
    // ignore every position it is sent and report its own unmoving one back, which pins the mount in place.
    @Override
    protected boolean isLocalClientAuthoritative() {
        return false;
    }

    @Override
    public int seats() {
        MountAbilityType mount = this.mount();
        return mount == null ? 0 : mount.seats();
    }

    @Override
    public int freeSeats() {
        return Math.max(0, this.seats() - this.getPassengers().size());
    }

    // The definition the mount is flying under, for the callers that have to run something of its own.
    @Override
    public Optional<MountAbilityType> mountDefinition() {
        return Optional.ofNullable(this.mount());
    }

    private MountAbilityType mount() {
        ItemStack visual = this.visual();
        if (this.resolvedVisual == null || !ItemStack.matches(this.resolvedVisual, visual)) {
            this.resolvedVisual = visual.copy();
            this.resolvedMount = ArtifactService.mount(this.level().registryAccess(), visual).orElse(null);
        }
        return this.resolvedMount;
    }

    @Override
    public @NonNull EntityDimensions getDimensions(@NonNull Pose pose) {
        MountAbilityType mount = this.mount();
        if (mount == null) return super.getDimensions(pose);
        EntityAttachments.Builder attachments = EntityAttachments.builder();
        // The points a rider's feet land on, in passenger order: the vanilla reader picks them by that index, which is
        // also what puts the second rider behind the first.
        for (int seat = 0; seat < mount.seats(); seat++)
            attachments.attach(EntityAttachment.PASSENGER, mount.seatOffset(seat));
        return EntityDimensions.fixed((float) mount.width(), (float) mount.height()).withAttachments(attachments);
    }

    @Override
    public float maxUpStep() {
        MountAbilityType mount = this.mount();
        return mount == null ? super.maxUpStep() : (float) mount.stepHeight();
    }

    // Everyone a mount carries shares one pose: the platform asks the vehicle, not the seat.
    @Override
    public boolean shouldRiderSit() {
        MountAbilityType mount = this.mount();
        return mount != null && mount.sit();
    }

    @Override
    protected boolean canAddPassenger(@NonNull Entity passenger) {
        MountAbilityType mount = this.mount();
        return mount != null && this.getPassengers().size() < mount.seats();
    }

    // The driver is whoever mounted first, which is the rider the flight skill put on: seat 0 is the seat it was
    // started from, and the input the movement reads is that rider's own.
    @Override
    public LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof LivingEntity driver ? driver : null;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public float getPickRadius() {
        return PICK_RADIUS;
    }

    // Boarding is the mount's only interaction: the skill starts a flight, and anyone else who asks for a free seat
    // rides along. The vanilla checks (not sneaking, not already riding, a seat left) decide, so no force here.
    @Override
    public @NonNull InteractionResult interact(@NonNull Player player, @NonNull InteractionHand hand, @NonNull Vec3 location) {
        if (player.isPassenger()) return InteractionResult.PASS;
        if (this.level().isClientSide()) return InteractionResult.SUCCESS;
        return this.canAddPassenger(player) && player.startRiding(this) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    // NeoForge poses the rider sitting by default; a flying sword is stood on unless its definition says otherwise.
    @Override
    public InterpolationHandler getInterpolation() {
        return this.interpolation;
    }

    @Override
    protected void defineSynchedData(@NonNull Builder builder) {
        builder.define(DATA_VISUAL, ItemStack.EMPTY);
    }

    // Both sides read the geometry off the stack, so a synced visual has to re-measure an entity that already exists.
    @Override
    public void onSyncedDataUpdated(@NonNull EntityDataAccessor<?> accessor) {
        if (DATA_VISUAL.equals(accessor)) {
            this.resolvedVisual = null;
            this.refreshDimensions();
        }
        super.onSyncedDataUpdated(accessor);
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        this.setVisual(input.read("visual", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        this.owner = input.read("owner", Codec.STRING).map(FlyingSwordEntity::ownerOf).orElse(null);
    }

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        ItemStack visual = this.visual();
        if (!visual.isEmpty()) output.store("visual", ItemStack.OPTIONAL_CODEC, visual);
        if (this.owner != null) output.store("owner", Codec.STRING, this.owner.getUUID().toString());
    }

    // The owner stays on disk as the plain UUID string it has always been, and is only wrapped for the contract.
    private static EntityReference<LivingEntity> ownerOf(String raw) {
        try {
            return EntityReference.of(UUID.fromString(raw));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    @Override
    public boolean hurtServer(@NonNull ServerLevel level, @NonNull DamageSource source, float amount) {
        return false;
    }
}
