package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.secretrealm.SecretRealmGeneration;
import com.iafenvoy.mxt.data.secretrealm.builtin.*;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The secret realm generation strategies a data pack can name in {@code generation.type}.
 */
@SuppressWarnings("unused")
public final class MxtSecretRealmGenerations {
    public static final DeferredRegister<MapCodec<? extends SecretRealmGeneration>> REGISTRY = DeferredRegister.create(MxtRegistries.SECRET_REALM_GENERATION_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends SecretRealmGeneration>, MapCodec<StemGeneration>> STEM = REGISTRY.register("stem", () -> StemGeneration.CODEC);
    public static final DeferredHolder<MapCodec<? extends SecretRealmGeneration>, MapCodec<FlatGeneration>> FLAT = REGISTRY.register("flat", () -> FlatGeneration.CODEC);
    public static final DeferredHolder<MapCodec<? extends SecretRealmGeneration>, MapCodec<VoidGeneration>> VOID = REGISTRY.register("void", () -> VoidGeneration.CODEC);
    public static final DeferredHolder<MapCodec<? extends SecretRealmGeneration>, MapCodec<TemplateGeneration>> TEMPLATE = REGISTRY.register("template", () -> TemplateGeneration.CODEC);
    public static final DeferredHolder<MapCodec<? extends SecretRealmGeneration>, MapCodec<ExistingGeneration>> EXISTING = REGISTRY.register("existing", () -> ExistingGeneration.CODEC);
}
