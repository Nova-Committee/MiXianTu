package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.condition.builtin.item.*;
import com.iafenvoy.saturn.data.condition.ItemCondition;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtItemConditions {
    public static final DeferredRegister<MapCodec<? extends ItemCondition>> REGISTRY = DeferredRegister.create(SaturnRegistries.ITEM_CONDITION_TYPE, MiXianTu.MOD_ID);


    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<OwnedByItemCondition>> OWNED_BY = REGISTRY.register("owned_by", () -> OwnedByItemCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<ArtifactEnergyRangeItemCondition>> ENERGY_RANGE = REGISTRY.register("energy_range", () -> ArtifactEnergyRangeItemCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<SpiritStorageNotFullCondition>> SPIRIT_STORAGE_NOT_FULL = REGISTRY.register("spirit_storage_not_full", () -> SpiritStorageNotFullCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<ItemElementCondition>> ELEMENT = REGISTRY.register("item_element", () -> ItemElementCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<CurseContainerItemCondition>> CURSE_CONTAINER = REGISTRY.register("curse_container", () -> CurseContainerItemCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<ItemQualityCondition>> ITEM_QUALITY = REGISTRY.register("item_quality", () -> ItemQualityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<ItemAbilitiesItemCondition>> ITEM_ABILITIES = REGISTRY.register("item_abilities", () -> ItemAbilitiesItemCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<ItemPhysiqueCondition>> ITEM_PHYSIQUE = REGISTRY.register("item_physique", () -> ItemPhysiqueCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<ItemTechniqueCondition>> ITEM_TECHNIQUE = REGISTRY.register("item_technique", () -> ItemTechniqueCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<ItemSpiritRootCondition>> ITEM_SPIRIT_ROOT = REGISTRY.register("item_spirit_root", () -> ItemSpiritRootCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemCondition>, MapCodec<ItemTalismanCondition>> ITEM_TALISMAN = REGISTRY.register("item_talisman", () -> ItemTalismanCondition.CODEC);
}
