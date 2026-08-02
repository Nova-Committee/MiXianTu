package com.iafenvoy.mxt.data.action.builtin;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * Creates a projectile entity, assigns the actor as owner and gives it a formula-driven velocity.
 */
public record SpawnProjectileEntityAction(EntityType<?> entityType, NumberProvider velocityX, NumberProvider velocityY,
                                          NumberProvider velocityZ) implements EntityAction {
    public static final MapCodec<SpawnProjectileEntityAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity_type").forGetter(SpawnProjectileEntityAction::entityType),
            NumberProvider.CODEC.fieldOf("velocity_x").forGetter(SpawnProjectileEntityAction::velocityX),
            NumberProvider.CODEC.fieldOf("velocity_y").forGetter(SpawnProjectileEntityAction::velocityY),
            NumberProvider.CODEC.fieldOf("velocity_z").forGetter(SpawnProjectileEntityAction::velocityZ)
    ).apply(instance, SpawnProjectileEntityAction::new));

    @Override
    public void execute(Entity entity) {
        this.execute(entity, FormulaContext.EMPTY);
    }

    @Override
    public void execute(Entity entity, FormulaContext context) {
        double x = this.velocityX.evaluate(context), y = this.velocityY.evaluate(context), z = this.velocityZ.evaluate(context);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || entity.level().isClientSide()) return;
        Entity created = this.entityType.create(entity.level(), EntitySpawnReason.TRIGGERED);
        if (!(created instanceof Projectile projectile)) return;
        projectile.setOwner(entity);
        projectile.setPos(entity.getX(), entity.getEyeY(), entity.getZ());
        projectile.setYRot(entity.getYRot());
        projectile.setXRot(entity.getXRot());
        projectile.setDeltaMovement(x, y, z);
        entity.level().addFreshEntity(projectile);
    }

    @Override
    public MapCodec<SpawnProjectileEntityAction> codec() {
        return CODEC;
    }
}
