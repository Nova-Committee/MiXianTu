package com.iafenvoy.mxt.runtime.talisman;

import com.iafenvoy.mxt.data.Talisman;
import com.iafenvoy.mxt.data.talisman.TalismanUse;
import com.iafenvoy.mxt.registry.MxtEntityTypes;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * A thrown carrier, in flight. It remembers the inscription's id rather than the definition, because only an id can
 * be rebuilt on the other side and after a reload; the effect itself belongs to that definition's type, which is why
 * nothing here knows what the talisman does. Only the landing half of the definition runs - the caster's own action
 * already happened at the throw - and a throw whose thrower is gone lands with nobody to act with, so it does
 * nothing. An entity hit runs the target half and a block hit the block half: the paper is spent either way.
 */
public final class TalismanProjectileEntity extends ThrowableItemProjectile {
    private static final int MAX_LIFETIME_TICKS = 200;
    private static final EntityDataAccessor<String> TALISMAN = SynchedEntityData.defineId(TalismanProjectileEntity.class, EntityDataSerializers.STRING);

    public TalismanProjectileEntity(EntityType<? extends TalismanProjectileEntity> type, Level level) {
        super(type, level);
    }

    public TalismanProjectileEntity(Level level, LivingEntity owner, Holder<Talisman> definition, double speed) {
        this(MxtEntityTypes.TALISMAN_PROJECTILE.get(), level);
        this.setOwner(owner);
        this.setTalisman(definition);
        this.setPos(owner.getX(), owner.getEyeY() - 0.1D, owner.getZ());
        this.shootFromRotation(owner, owner.getXRot(), owner.getYRot(), 0.0F, (float) speed, 0.0F);
    }

    @Override
    protected void defineSynchedData(@NonNull Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TALISMAN, "");
    }

    @Override
    protected @NonNull Item getDefaultItem() {
        return MxtItems.TALISMAN.get();
    }

    // Read off the definition rather than carried as a field: the id travels with the projectile, so both sides
    // answer the same fall without a second synchronised value.
    @Override
    protected double getDefaultGravity() {
        Holder<Talisman> definition = this.talisman();
        return definition == null ? 0.0D : definition.value().type().gravity(FormulaContext.EMPTY);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && this.tickCount >= MAX_LIFETIME_TICKS) this.discard();
    }

    @Override
    protected void onHitEntity(@NonNull EntityHitResult hit) {
        super.onHitEntity(hit);
        this.land(hit.getEntity());
    }

    @Override
    protected void onHitBlock(@NonNull BlockHitResult hit) {
        super.onHitBlock(hit);
        if (this.level().isClientSide()) return;
        this.landOnBlock(hit);
        this.discard();
    }

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        super.readAdditionalSaveData(input);
        this.getEntityData().set(TALISMAN, input.getStringOr("talisman", ""));
    }

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("talisman", this.getEntityData().get(TALISMAN));
    }

    // The landing half of the definition's own use. Nothing happens when either side is missing: a bi-entity action
    // needs both, and an inscription a reload removed has no behaviour left to run.
    private void land(@Nullable Entity target) {
        if (target == null) return;
        TalismanUse use = this.use();
        if (use != null) use.definition().value().type().landOn(use, target);
    }

    // The block half, run at the block that was struck rather than at one entity.
    private void landOnBlock(BlockHitResult hit) {
        TalismanUse use = this.use();
        if (use != null)
            use.definition().value().type().landOnBlock(use, hit.getBlockPos(), hit.getDirection());
    }

    // Null when the definition has nobody to act with: an inscription a reload removed, or a throw whose thrower is
    // gone, which leaves both halves without their caster.
    private @Nullable TalismanUse use() {
        if (this.level().isClientSide()) return null;
        Holder<Talisman> definition = this.talisman();
        if (definition == null || !(this.getOwner() instanceof LivingEntity owner)) return null;
        return new TalismanUse(owner, FormulaContext.of(owner), this.position(), definition);
    }

    // Requires a registry holder: only the id travels, so an inline value could not be rebuilt on arrival.
    private void setTalisman(Holder<Talisman> definition) {
        Identifier id = definition.unwrapKey().map(ResourceKey::identifier)
                .orElseThrow(() -> new IllegalArgumentException("A thrown talisman must be a registry holder"));
        this.getEntityData().set(TALISMAN, id.toString());
    }

    private @Nullable Holder<Talisman> talisman() {
        Identifier id = Identifier.tryParse(this.getEntityData().get(TALISMAN));
        if (id == null) return null;
        Optional<Reference<Talisman>> found = this.level().registryAccess().lookupOrThrow(MxtResourceKeys.TALISMAN)
                .get(ResourceKey.create(MxtResourceKeys.TALISMAN, id));
        return found.orElse(null);
    }
}
