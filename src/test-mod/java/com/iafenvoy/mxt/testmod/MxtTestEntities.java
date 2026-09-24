package com.iafenvoy.mxt.testmod;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Entities;

/**
 * The entities the test mod adds so the framework's extension points have a live implementation: the probe beast
 * is what a content mod's own creature would be.
 */
public final class MxtTestEntities {
    public static final Entities REGISTRY = DeferredRegister.createEntities(MxtTestMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<ProbeBeast>> PROBE_BEAST =
            REGISTRY.registerEntityType("probe_beast", ProbeBeast::new, MobCategory.CREATURE,
                    b -> b.sized(0.6F, 0.85F).clientTrackingRange(10));
}
