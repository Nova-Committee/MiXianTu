package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.ability.type.FlightControlAbilityType;
import com.iafenvoy.mxt.data.ability.type.MountAbilityType;
import com.iafenvoy.mxt.data.ability.type.StorageAbilityType;
import com.iafenvoy.mxt.data.ability.type.WordAbilityType;
import com.iafenvoy.saturn.data.ability.AbilityType;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtAbilityTypes {
    public static final DeferredRegister<MapCodec<? extends AbilityType>> REGISTRY = DeferredRegister.create(SaturnRegistries.ABILITY_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<WordAbilityType>> WORD = REGISTRY.register("word", () -> WordAbilityType.CODEC);
    // What used to be the artifact ability table: two capabilities and a vehicle declaration, now ordinary ability
    // types, so any source that can grant an ability can grant storage, a periodic price or the skill that flies.
    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<MountAbilityType>> MOUNT = REGISTRY.register("mount", () -> MountAbilityType.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<FlightControlAbilityType>> FLIGHT_CONTROL = REGISTRY.register("flight_control", () -> FlightControlAbilityType.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<StorageAbilityType>> STORAGE = REGISTRY.register("storage", () -> StorageAbilityType.CODEC);
}
