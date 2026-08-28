package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.context.action.EntityActionContext;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.NonNull;

/**
 * Spawns a registered entity in the acting entity's current level.
 */
public record SpawnEntityAction(EntityType<?> entityType, NumberProvider x, NumberProvider y,
                                NumberProvider z) implements EntityAction {
    public static final MapCodec<SpawnEntityAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity_type").forGetter(SpawnEntityAction::entityType),
            NumberProvider.CODEC.fieldOf("x").forGetter(SpawnEntityAction::x),
            NumberProvider.CODEC.fieldOf("y").forGetter(SpawnEntityAction::y),
            NumberProvider.CODEC.fieldOf("z").forGetter(SpawnEntityAction::z)
    ).apply(i, SpawnEntityAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        double x = this.x.evaluate(context), y = this.y.evaluate(context), z = this.z.evaluate(context);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || entity.level().isClientSide()) return;
        Entity spawned = this.entityType.create(entity.level(), EntitySpawnReason.TRIGGERED);
        if (spawned == null) return;
        spawned.setPos(x, y, z);
        spawned.setYRot(entity.getYRot());
        spawned.setXRot(entity.getXRot());
        entity.level().addFreshEntity(spawned);
    }

    @Override
    public @NonNull MapCodec<SpawnEntityAction> codec() {
        return CODEC;
    }
}
