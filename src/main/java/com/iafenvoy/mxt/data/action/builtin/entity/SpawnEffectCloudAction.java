package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Creates a vanilla area-effect cloud at the actor's position.
 */
public record SpawnEffectCloudAction(float radius, float radiusOnUse, int waitTime,
                                     List<MobEffectInstance> effects) implements EntityAction {
    public static final MapCodec<SpawnEffectCloudAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.FLOAT.optionalFieldOf("radius", 3.0F).forGetter(SpawnEffectCloudAction::radius),
            Codec.FLOAT.optionalFieldOf("radius_on_use", -0.5F).forGetter(SpawnEffectCloudAction::radiusOnUse),
            Codec.INT.optionalFieldOf("wait_time", 10).forGetter(SpawnEffectCloudAction::waitTime),
            MobEffectInstance.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(SpawnEffectCloudAction::effects)
    ).apply(i, SpawnEffectCloudAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        AreaEffectCloud cloud = EntityType.AREA_EFFECT_CLOUD.create(level, EntitySpawnReason.TRIGGERED);
        if (cloud == null) return;
        cloud.setPos(entity.getX(), entity.getY(), entity.getZ());
        cloud.setRadius(this.radius);
        cloud.setRadiusOnUse(this.radiusOnUse);
        cloud.setWaitTime(this.waitTime);
        this.effects.stream().map(MobEffectInstance::new).forEach(cloud::addEffect);
        level.addFreshEntity(cloud);
    }

    @Override
    public @NonNull MapCodec<SpawnEffectCloudAction> codec() {
        return CODEC;
    }
}
