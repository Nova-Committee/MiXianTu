package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.particle.RiftParticleType;
import com.iafenvoy.mxt.particle.SpiritWispParticleOptions;
import com.iafenvoy.mxt.particle.SpiritWispParticleType;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MxtParticleTypes {
    public static final DeferredRegister<ParticleType<?>> REGISTRY = DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, ParticleType<SpiritWispParticleOptions>> SPIRIT_WISP = REGISTRY.register("spirit_wisp", SpiritWispParticleType::new);
    // The vanilla portal particle, but taking the colour of the rift it comes from instead of the portal's fixed
    // violet; the colour rides along as a ColorParticleOption, so a rift sends nothing extra for its particles.
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> RIFT = REGISTRY.register("rift", RiftParticleType::new);
}
