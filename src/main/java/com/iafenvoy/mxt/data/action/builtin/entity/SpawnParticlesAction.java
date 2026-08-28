package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.context.action.EntityActionContext;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

public record SpawnParticlesAction(ParticleOptions particle, BiEntityCondition biEntityCondition, int count,
                                   float speed, boolean force, Vec3 spread, float offsetX, float offsetY,
                                   float offsetZ) implements EntityAction {
    public static final MapCodec<SpawnParticlesAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ParticleTypes.CODEC.fieldOf("particle").forGetter(SpawnParticlesAction::particle),
            BiEntityCondition.optionalCodec("bientity_condition").forGetter(SpawnParticlesAction::biEntityCondition),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("count").forGetter(SpawnParticlesAction::count),
            Codec.FLOAT.optionalFieldOf("speed", 0.0F).forGetter(SpawnParticlesAction::speed),
            Codec.BOOL.optionalFieldOf("force", false).forGetter(SpawnParticlesAction::force),
            Vec3.CODEC.optionalFieldOf("spread", new Vec3(0.5D, 0.5D, 0.5D)).forGetter(SpawnParticlesAction::spread),
            Codec.FLOAT.optionalFieldOf("offset_x", 0.0F).forGetter(SpawnParticlesAction::offsetX),
            Codec.FLOAT.optionalFieldOf("offset_y", 0.5F).forGetter(SpawnParticlesAction::offsetY),
            Codec.FLOAT.optionalFieldOf("offset_z", 0.0F).forGetter(SpawnParticlesAction::offsetZ)
    ).apply(i, SpawnParticlesAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        Vec3 delta = this.spread.multiply(entity.getBbWidth(), entity.getEyeHeight(), entity.getBbWidth());
        Vec3 position = entity.position().add(this.offsetX, this.offsetY, this.offsetZ);
        for (ServerPlayer player : level.players()) {
            if (this.biEntityCondition.test(entity, player, ctx))
                level.sendParticles(player, this.particle, this.force, false, position.x, position.y, position.z, this.count, delta.x, delta.y, delta.z, this.speed);
        }
    }

    @Override
    public @NonNull MapCodec<SpawnParticlesAction> codec() {
        return CODEC;
    }
}
