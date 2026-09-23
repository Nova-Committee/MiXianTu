package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
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

    // The passenger attachment is where the rider's feet stand. The mount's blade sits in the collision box, but how
    // tall that blade is depends on the item's model, which only the client can measure: 0.6 clears the thick sword
    // models in use with room for the blade to tip. A per-definition ride_offset is the real answer, and this constant
    // is what it will default to.
    public static final DeferredHolder<EntityType<?>, EntityType<FlyingSwordEntity>> FLYING_SWORD = REGISTRY.registerEntityType("flying_sword", FlyingSwordEntity::new, MobCategory.MISC, b -> b.noLootTable().sized(0.35F, 0.12F).passengerAttachments(0.65F).clientTrackingRange(10).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<SoulEntity>> SOUL = REGISTRY.registerEntityType("soul", SoulEntity::new, MobCategory.MISC, b -> b.noLootTable().sized(0.3F, 0.5F).clientTrackingRange(8).updateInterval(20));
    public static final DeferredHolder<EntityType<?>, EntityType<SpiritBurstEntity>> SPIRIT_BURST = REGISTRY.registerEntityType("spirit_burst", SpiritBurstEntity::new, MobCategory.MISC, b -> b.noLootTable().sized(0.0F, 0.0F).clientTrackingRange(8).updateInterval(1));
    public static final DeferredHolder<EntityType<?>, EntityType<ColoredLightningBolt>> COLORED_LIGHTNING = REGISTRY.registerEntityType("colored_lightning", ColoredLightningBolt::new, MobCategory.MISC, b -> b.noLootTable().noSave().sized(0.0F, 0.0F).clientTrackingRange(16).updateInterval(Integer.MAX_VALUE));
}
