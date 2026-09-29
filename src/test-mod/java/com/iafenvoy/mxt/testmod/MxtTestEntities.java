package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.data.ability.type.MountAbilityType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Entities;

/**
 * The entities the test mod adds so the framework's extension points have a live implementation: the probe beast
 * is what a content mod's own creature would be, and the probe mount is what its own vehicle would be.
 */
public final class MxtTestEntities {
    public static final Entities REGISTRY = DeferredRegister.createEntities(MxtTestMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<ProbeBeast>> PROBE_BEAST =
            REGISTRY.registerEntityType("probe_beast", ProbeBeast::new, MobCategory.CREATURE,
                    b -> b.sized(0.6F, 0.85F).clientTrackingRange(10));

    public static final DeferredHolder<EntityType<?>, EntityType<ProbeMount>> PROBE_MOUNT =
            REGISTRY.registerEntityType("probe_mount", ProbeMount::new, MobCategory.MISC,
                    b -> b.noLootTable().sized((float) MountAbilityType.DEFAULT_WIDTH, (float) MountAbilityType.DEFAULT_HEIGHT).clientTrackingRange(10));
}
