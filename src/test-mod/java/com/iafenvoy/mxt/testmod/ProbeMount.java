package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.api.MountVehicle;
import com.iafenvoy.mxt.data.ability.type.MountAbilityType;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.mojang.serialization.Codec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * A vehicle belonging to the test mod, which is the shape an addon's own mount takes: the entity type is what a
 * {@code mxt:mount} names under {@code entity_type}, and the framework only ever reaches it through MountVehicle.
 *
 * <p>Moving is deliberately left out - what a vehicle does while it flies is the addon's own code - and the artifact
 * is not this class's business either: the framework takes it back when the body leaves the world. The probe asserts
 * that this is what a naming definition spawns.
 */
public final class ProbeMount extends Entity implements MountVehicle {
    private static final EntityDataAccessor<ItemStack> DATA_VISUAL =
            SynchedEntityData.defineId(ProbeMount.class, EntityDataSerializers.ITEM_STACK);
    private EntityReference<LivingEntity> owner;
    private double speed = 0.05D;
    private ItemStack resolvedVisual;
    private MountAbilityType resolvedMount;

    public ProbeMount(EntityType<? extends ProbeMount> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    @Override
    public void setVisual(ItemStack visual) {
        this.entityData.set(DATA_VISUAL, visual);
        this.resolvedVisual = null;
    }

    @Override
    public ItemStack visual() {
        return this.entityData.get(DATA_VISUAL);
    }

    @Override
    public void setOwner(UUID owner) {
        this.owner = owner == null ? null : EntityReference.of(owner);
    }

    @Override
    public @Nullable EntityReference<LivingEntity> getOwnerReference() {
        return this.owner;
    }

    @Override
    public void setFlightSpeed(double speed) {
        this.speed = Math.clamp(speed, 0.01D, 1.0D);
    }

    @Override
    public int seats() {
        return this.mount().map(MountAbilityType::seats).orElse(0);
    }

    @Override
    public int freeSeats() {
        return Math.max(0, this.seats() - this.getPassengers().size());
    }

    @Override
    public Optional<MountAbilityType> mountDefinition() {
        return this.mount();
    }

    // The definition is read off the stack the vehicle carries, exactly as the framework's own vehicle reads it.
    private Optional<MountAbilityType> mount() {
        ItemStack visual = this.visual();
        if (this.resolvedVisual == null || !ItemStack.matches(this.resolvedVisual, visual)) {
            this.resolvedVisual = visual.copy();
            this.resolvedMount = ArtifactService.mount(this.level().registryAccess(), visual).orElse(null);
        }
        return Optional.ofNullable(this.resolvedMount);
    }

    @Override
    protected void defineSynchedData(@NonNull Builder builder) {
        builder.define(DATA_VISUAL, ItemStack.EMPTY);
    }

    // The same as the framework's own vehicle: a flight ends by landing, so a hit point total would say nothing.
    @Override
    public boolean hurtServer(@NonNull ServerLevel level, @NonNull DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        this.setVisual(input.read("visual", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        this.owner = input.read("owner", Codec.STRING).map(ProbeMount::ownerOf).orElse(null);
    }

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        ItemStack visual = this.visual();
        if (!visual.isEmpty()) output.store("visual", ItemStack.OPTIONAL_CODEC, visual);
        if (this.owner != null) output.store("owner", Codec.STRING, this.owner.getUUID().toString());
    }

    private static EntityReference<LivingEntity> ownerOf(String raw) {
        try {
            return EntityReference.of(UUID.fromString(raw));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
