package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.artifact.ability.*;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtArtifactAbilityTypes {
    public static final DeferredRegister<MapCodec<? extends ArtifactAbility>> REGISTRY =
            DeferredRegister.create(MxtRegistries.ARTIFACT_ABILITY_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends ArtifactAbility>, MapCodec<EmptyArtifactAbility>> EMPTY =
            REGISTRY.register("empty", () -> EmptyArtifactAbility.CODEC);
    public static final DeferredHolder<MapCodec<? extends ArtifactAbility>, MapCodec<GrantArtifactAbility>> PASSIVE =
            REGISTRY.register("passive", () -> GrantArtifactAbility.PASSIVE_CODEC);
    public static final DeferredHolder<MapCodec<? extends ArtifactAbility>, MapCodec<GrantArtifactAbility>> ACTIVE =
            REGISTRY.register("active", () -> GrantArtifactAbility.ACTIVE_CODEC);
    public static final DeferredHolder<MapCodec<? extends ArtifactAbility>, MapCodec<FlightArtifactAbility>> FLIGHT =
            REGISTRY.register("flight", () -> FlightArtifactAbility.CODEC);
    public static final DeferredHolder<MapCodec<? extends ArtifactAbility>, MapCodec<StorageArtifactAbility>> STORAGE =
            REGISTRY.register("storage", () -> StorageArtifactAbility.CODEC);
    public static final DeferredHolder<MapCodec<? extends ArtifactAbility>, MapCodec<UpkeepArtifactAbility>> UPKEEP =
            REGISTRY.register("upkeep", () -> UpkeepArtifactAbility.CODEC);
}
