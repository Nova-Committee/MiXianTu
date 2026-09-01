package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.loot.*;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtLootConditions {
    public static final DeferredRegister<MapCodec<? extends LootItemCondition>> REGISTRY = DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends LootItemCondition>, MapCodec<HasAbilityLootCondition>> HAS_ABILITY = REGISTRY.register("has_ability", () -> HasAbilityLootCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends LootItemCondition>, MapCodec<HasCurseLootCondition>> HAS_CURSE = REGISTRY.register("has_curse", () -> HasCurseLootCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends LootItemCondition>, MapCodec<RealmLootCondition>> REALM = REGISTRY.register("realm", () -> RealmLootCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends LootItemCondition>, MapCodec<HasSpiritRootLootCondition>> HAS_SPIRIT_ROOT = REGISTRY.register("has_spirit_root", () -> HasSpiritRootLootCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends LootItemCondition>, MapCodec<HasPhysiqueLootCondition>> HAS_PHYSIQUE = REGISTRY.register("has_physique", () -> HasPhysiqueLootCondition.CODEC);
}
