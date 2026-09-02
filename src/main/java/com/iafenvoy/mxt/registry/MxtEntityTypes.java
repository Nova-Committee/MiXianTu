package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.runtime.artifact.FlyingSwordEntity;
import com.iafenvoy.mxt.runtime.spirit.SpiritBurstEntity;
import com.iafenvoy.mxt.runtime.world.SoulEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Entities;

public final class MxtEntityTypes {
    public static final Entities REGISTRY = DeferredRegister.createEntities(MiXianTu.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<FlyingSwordEntity>> FLYING_SWORD = REGISTRY.registerEntityType("flying_sword", FlyingSwordEntity::new, MobCategory.MISC, b -> b.noLootTable().sized(0.35F, 0.12F).passengerAttachments(0.35F).clientTrackingRange(10).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<SoulEntity>> SOUL = REGISTRY.registerEntityType("soul", SoulEntity::new, MobCategory.MISC, b -> b.noLootTable().sized(0.3F, 0.5F).clientTrackingRange(8).updateInterval(20));
    public static final DeferredHolder<EntityType<?>, EntityType<SpiritBurstEntity>> SPIRIT_BURST = REGISTRY.registerEntityType("spirit_burst", SpiritBurstEntity::new, MobCategory.MISC, b -> b.noLootTable().sized(0.0F, 0.0F).clientTrackingRange(8).updateInterval(1));
}
