package com.iafenvoy.mxt.runtime.spirit;

import com.iafenvoy.mxt.api.AuraAccess;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.particle.SpiritWispParticleOptions;
import com.iafenvoy.mxt.registry.MxtEntityTypes;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Server-authoritative spirit-power projectile. It carries a {@link Resource} - the aura kind. The element a
 * value is attuned to is the cultivation profile's {@code aura_type} and is not named here, so the two are
 * different fields and must not share a name.
 */
public final class SpiritBurstEntity extends ThrowableProjectile {
    private static final int MAX_LIFETIME_TICKS = 100;
    private static final double PARTICLE_SPACING = 0.16D;
    private static final float TRAIL_PARTICLE_SIZE = 0.085F;
    private static final EntityDataAccessor<Integer> AMOUNT = SynchedEntityData.defineId(SpiritBurstEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PARTICLE_COLOR = SynchedEntityData.defineId(SpiritBurstEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> AURA = SynchedEntityData.defineId(SpiritBurstEntity.class, EntityDataSerializers.STRING);

    public SpiritBurstEntity(EntityType<? extends SpiritBurstEntity> type, Level level) {
        super(type, level);
    }

    public SpiritBurstEntity(Level level, Player owner, Holder<Aura> aura, int amount, int particleColor) {
        this(MxtEntityTypes.SPIRIT_BURST.get(), level);
        this.setOwner(owner);
        this.setAura(aura);
        this.setAmount(amount);
        this.setParticleColor(particleColor);
        this.setPos(owner.getX(), owner.getEyeY() - 0.1D, owner.getZ());
        this.shootFromRotation(owner, owner.getXRot(), owner.getYRot(), 0.0F, 1.25F, 0.0F);
    }

    @Override
    protected void defineSynchedData(@NonNull Builder builder) {
        builder.define(AMOUNT, 0);
        builder.define(PARTICLE_COLOR, 0xFFFFFF);
        builder.define(AURA, "");
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide() && this.tryHitAuraAccess()) return;
        super.tick();
        if (this.level().isClientSide()) {
            this.spawnTrailParticles();
        } else if (this.tickCount >= MAX_LIFETIME_TICKS) {
            this.discard();
        }
    }

    @Override
    protected boolean canHitEntity(@NonNull Entity entity) {
        return false;
    }

    @Override
    protected void onHitBlock(@NonNull BlockHitResult hit) {
        if (!this.level().isClientSide()) {
            BlockPos pos = hit.getBlockPos();
            BlockEntity blockEntity = this.level().getBlockEntity(pos);
            if (blockEntity instanceof AuraAccess accessor) {
                this.aura().ifPresent(type -> accessor.insert(this.ownerLivingEntity(), type, this.amount(), false));
            }
            this.discard();
        }
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0D;
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        this.setAmount(input.getIntOr("amount", 0));
        this.setParticleColor(input.getIntOr("particle_color", 0xFFFFFF));
        this.getEntityData().set(AURA, input.getStringOr("aura", ""));
    }

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        output.putInt("amount", this.amount());
        output.putInt("particle_color", this.particleColor());
        output.putString("aura", this.getEntityData().get(AURA));
    }

    @Override
    public boolean hurtServer(@NonNull ServerLevel level, @NonNull DamageSource source, float amount) {
        return false;
    }

    public int amount() {
        return this.getEntityData().get(AMOUNT);
    }

    public void setAmount(int amount) {
        this.getEntityData().set(AMOUNT, AuraAccess.requireNonNegative(amount));
    }

    public int particleColor() {
        return this.getEntityData().get(PARTICLE_COLOR);
    }

    public void setParticleColor(int particleColor) {
        if (particleColor < 0 || particleColor > 0xFFFFFF)
            throw new IllegalArgumentException("Particle color must be an RGB value");
        this.getEntityData().set(PARTICLE_COLOR, particleColor);
    }

    // Requires a registry holder: only the id travels, so an inline value could not be rebuilt on arrival.
    public void setAura(Holder<Aura> aura) {
        Identifier id = aura.unwrapKey().map(ResourceKey::identifier)
                .orElseThrow(() -> new IllegalArgumentException("Spirit burst aura must be a registry holder"));
        this.getEntityData().set(AURA, id.toString());
    }

    private Optional<Reference<Aura>> aura() {
        Identifier id = Identifier.tryParse(this.getEntityData().get(AURA));
        if (id == null) return Optional.empty();
        return this.level().registryAccess().lookupOrThrow(MxtResourceKeys.AURA)
                .get(ResourceKey.create(MxtResourceKeys.AURA, id));
    }

    private void spawnTrailParticles() {
        Vec3 current = this.position();
        Vec3 previous = new Vec3(this.xo, this.yo, this.zo);
        Vec3 movement = current.subtract(previous);
        int samples = Math.max(1, (int) Math.ceil(movement.length() / PARTICLE_SPACING));
        // The first sample is the previous tick's last one; skipping it avoids regularly spaced bright clumps.
        for (int index = 1; index <= samples; index++) {
            Vec3 point = previous.lerp(current, (double) index / samples);
            int color = this.variedParticleColor();
            this.level().addParticle(new SpiritWispParticleOptions(color, TRAIL_PARTICLE_SIZE), point.x, point.y, point.z,
                    0.0D, 0.0D, 0.0D);
        }
    }

    private int variedParticleColor() {
        int color = this.particleColor();
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        if (0.2126F * red + 0.7152F * green + 0.0722F * blue < 0.1F) {
            red = Math.min(1.0F, red + this.random.nextFloat() * 0.125F);
            green = Math.min(1.0F, green + this.random.nextFloat() * 0.125F);
            blue = Math.min(1.0F, blue + this.random.nextFloat() * 0.125F);
        }
        return ((int) (red * 255.0F) << 16) | ((int) (green * 255.0F) << 8) | (int) (blue * 255.0F);
    }

    // The whole cell the block stands in plus the one above it: a display stand shows its item 1.65 above a shape
    // that stops at 1.4375, so a shape-based hit flies over the stand and pours into nothing.
    private static final double SPIRIT_REACH_HEIGHT = 2.0D;

    // Vanilla ray casts only test the block cell currently crossed, and a container is reached as a place
    // rather than as its collision shape; a regular block hit still wins when it is closer.
    private boolean tryHitAuraAccess() {
        Vec3 start = this.position();
        Vec3 end = start.add(this.getDeltaMovement());
        double closestDistance = Double.POSITIVE_INFINITY;
        BlockPos closestPos = null;

        BlockPos min = BlockPos.containing(Math.min(start.x, end.x) - 1.0D, Math.min(start.y, end.y) - 1.0D,
                Math.min(start.z, end.z) - 1.0D);
        BlockPos max = BlockPos.containing(Math.max(start.x, end.x) + 1.0D, Math.max(start.y, end.y) + 1.0D,
                Math.max(start.z, end.z) + 1.0D);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockEntity blockEntity = this.level().getBlockEntity(pos);
            if (!(blockEntity instanceof AuraAccess)) continue;

            AABB reach = new AABB(pos).expandTowards(0.0D, SPIRIT_REACH_HEIGHT - 1.0D, 0.0D);
            Optional<Vec3> reached = reach.clip(start, end);
            if (reached.isEmpty()) continue;

            double distance = start.distanceToSqr(reached.get());
            if (distance < closestDistance) {
                closestDistance = distance;
                closestPos = pos.immutable();
            }
        }

        if (closestPos == null) return false;

        BlockHitResult blockHit = this.level().clip(new ClipContext(start, end,
                Block.COLLIDER, Fluid.NONE, this));
        if (blockHit.getType() != BlockHitResult.Type.MISS && start.distanceToSqr(blockHit.getLocation()) <= closestDistance)
            return false;

        BlockEntity blockEntity = this.level().getBlockEntity(closestPos);
        if (!(blockEntity instanceof AuraAccess accessor)) return false;
        this.aura().ifPresent(type -> accessor.insert(this.ownerLivingEntity(), type, this.amount(), false));
        this.discard();
        return true;
    }

    private LivingEntity ownerLivingEntity() {
        return this.getOwner() instanceof LivingEntity living ? living : null;
    }
}
