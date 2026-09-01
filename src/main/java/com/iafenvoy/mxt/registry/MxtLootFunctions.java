package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.loot.ApplyCurseLootFunction;
import com.iafenvoy.mxt.loot.GrantAbilityLootFunction;
import com.iafenvoy.mxt.loot.SetArtifactOwnerLootFunction;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtLootFunctions {
    public static final DeferredRegister<MapCodec<? extends LootItemFunction>> REGISTRY = DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends LootItemFunction>, MapCodec<GrantAbilityLootFunction>> GRANT_ABILITY = REGISTRY.register("grant_ability", () -> GrantAbilityLootFunction.CODEC);
    public static final DeferredHolder<MapCodec<? extends LootItemFunction>, MapCodec<SetArtifactOwnerLootFunction>> SET_ARTIFACT_OWNER = REGISTRY.register("set_artifact_owner", () -> SetArtifactOwnerLootFunction.CODEC);
    public static final DeferredHolder<MapCodec<? extends LootItemFunction>, MapCodec<ApplyCurseLootFunction>> APPLY_CURSE = REGISTRY.register("apply_curse", () -> ApplyCurseLootFunction.CODEC);
}
