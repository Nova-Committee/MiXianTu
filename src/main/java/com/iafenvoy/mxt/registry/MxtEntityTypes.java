package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.runtime.artifact.FlyingSwordEntity;
import com.iafenvoy.mxt.runtime.spirit.SpiritBurstEntity;
import com.iafenvoy.mxt.runtime.world.SoulEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Code-owned entities needed by framework physics rather than by datapack-only content.
 */
public final class MxtEntityTypes {
    public static final DeferredRegister<EntityType<?>> REGISTRY = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<EntityType<?>, EntityType<FlyingSwordEntity>> FLYING_SWORD = REGISTRY.register("flying_sword", () ->
            Builder.of(FlyingSwordEntity::new, MobCategory.MISC).noLootTable().sized(0.35F, 0.12F)
                    .passengerAttachments(0.35F).clientTrackingRange(10).updateInterval(1)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "flying_sword"))));
    public static final DeferredHolder<EntityType<?>, EntityType<SoulEntity>> SOUL = REGISTRY.register("soul", () ->
            Builder.of(SoulEntity::new, MobCategory.MISC).noLootTable().sized(0.3F, 0.5F)
                    .clientTrackingRange(8).updateInterval(20)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "soul"))));
    public static final DeferredHolder<EntityType<?>, EntityType<SpiritBurstEntity>> SPIRIT_BURST = REGISTRY.register("spirit_burst", () ->
            Builder.<SpiritBurstEntity>of(SpiritBurstEntity::new, MobCategory.MISC).noLootTable().sized(0.0F, 0.0F)
                    .clientTrackingRange(8).updateInterval(1)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "spirit_burst"))));

    private MxtEntityTypes() {
    }
}
