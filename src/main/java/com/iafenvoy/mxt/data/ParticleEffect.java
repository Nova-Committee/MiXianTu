package com.iafenvoy.mxt.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative particle emission configuration shared by data-driven systems.
 */
public record ParticleEffect(ParticleOptions particle, int count, float speed, boolean force,
                             Vec3 spread, float offsetX, float offsetY, float offsetZ) {
    public static final ParticleEffect DEFAULT_BREAKTHROUGH = new ParticleEffect(
            ParticleTypes.END_ROD, 20, 0.03F, false,
            new Vec3(0.6D, 1.0D, 0.6D), 0.0F, 0.8F, 0.0F);

    public static final Codec<ParticleEffect> CODEC = RecordCodecBuilder.create(i -> i.group(
            ParticleTypes.CODEC.fieldOf("particle").forGetter(ParticleEffect::particle),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("count", 16).forGetter(ParticleEffect::count),
            Codec.FLOAT.optionalFieldOf("speed", 0.0F).forGetter(ParticleEffect::speed),
            Codec.BOOL.optionalFieldOf("force", false).forGetter(ParticleEffect::force),
            Vec3.CODEC.optionalFieldOf("spread", new Vec3(0.5D, 0.5D, 0.5D)).forGetter(ParticleEffect::spread),
            Codec.FLOAT.optionalFieldOf("offset_x", 0.0F).forGetter(ParticleEffect::offsetX),
            Codec.FLOAT.optionalFieldOf("offset_y", 0.5F).forGetter(ParticleEffect::offsetY),
            Codec.FLOAT.optionalFieldOf("offset_z", 0.0F).forGetter(ParticleEffect::offsetZ)
    ).apply(i, ParticleEffect::new));

    /**
     * Broadcasts this effect to players in the server level.
     */
    public void send(ServerLevel level, Vec3 position) {
        Vec3 target = position.add(this.offsetX, this.offsetY, this.offsetZ);
        for (ServerPlayer player : level.players()) {
            this.dispatch(level, player, target);
        }
    }

    /**
     * Sends this effect only to one client, useful for per-player environment overlays.
     */
    public void sendTo(ServerLevel level, ServerPlayer player, Vec3 position) {
        Vec3 target = position.add(this.offsetX, this.offsetY, this.offsetZ);
        this.dispatch(level, player, target);
    }

    private void dispatch(ServerLevel level, ServerPlayer player, Vec3 target) {
        level.sendParticles(player, this.particle, this.force, false,
                target.x(), target.y(), target.z(), this.count,
                this.spread.x(), this.spread.y(), this.spread.z(), this.speed);
    }
}
