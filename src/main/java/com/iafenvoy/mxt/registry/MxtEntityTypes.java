package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.ability.type.MountAbilityType;
import com.iafenvoy.mxt.runtime.artifact.FlyingSwordEntity;
import com.iafenvoy.mxt.runtime.lightning.ColoredLightningBolt;
import com.iafenvoy.mxt.runtime.spirit.SpiritBurstEntity;
import com.iafenvoy.mxt.runtime.world.SoulEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Entities;

public final class MxtEntityTypes {
    public static final Entities REGISTRY = DeferredRegister.createEntities(MiXianTu.MOD_ID);

    // These values are only what a mount with no readable definition falls back to. A real one takes its box, its ride
    // height, its seat count and its pose from the `mxt:mount` entry the vehicle carries, on both sides.
    public static final DeferredHolder<EntityType<?>, EntityType<FlyingSwordEntity>> FLYING_SWORD = REGISTRY.registerEntityType("flying_sword", FlyingSwordEntity::new, MobCategory.MISC, b -> b.noLootTable().sized((float) MountAbilityType.DEFAULT_WIDTH, (float) MountAbilityType.DEFAULT_HEIGHT).passengerAttachments((float) MountAbilityType.DEFAULT_SEAT_HEIGHT).clientTrackingRange(10).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<SoulEntity>> SOUL = REGISTRY.registerEntityType("soul", SoulEntity::new, MobCategory.MISC, b -> b.noLootTable().sized(0.3F, 0.5F).clientTrackingRange(8).updateInterval(20));
    public static final DeferredHolder<EntityType<?>, EntityType<SpiritBurstEntity>> SPIRIT_BURST = REGISTRY.registerEntityType("spirit_burst", SpiritBurstEntity::new, MobCategory.MISC, b -> b.noLootTable().sized(0.0F, 0.0F).clientTrackingRange(8).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<ColoredLightningBolt>> COLORED_LIGHTNING = REGISTRY.registerEntityType("colored_lightning", ColoredLightningBolt::new, MobCategory.MISC, b -> b.noLootTable().noSave().sized(0.0F, 0.0F).clientTrackingRange(16).updateInterval(Integer.MAX_VALUE));
}
