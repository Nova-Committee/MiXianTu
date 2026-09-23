package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.secretrealm.SecretRealmGeneration;
import com.iafenvoy.mxt.data.secretrealm.SecretRealmGeneration.Existing;
import com.iafenvoy.mxt.data.secretrealm.SecretRealmGeneration.Flat;
import com.iafenvoy.mxt.data.secretrealm.SecretRealmGeneration.Stem;
import com.iafenvoy.mxt.data.secretrealm.SecretRealmGeneration.Template;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The secret realm generation strategies a data pack can name in {@code generation.type}.
 */
@SuppressWarnings("unused")
public final class MxtSecretRealmGenerations {
    public static final DeferredRegister<MapCodec<? extends SecretRealmGeneration>> REGISTRY = DeferredRegister.create(MxtRegistries.SECRET_REALM_GENERATION_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends SecretRealmGeneration>, MapCodec<Stem>> STEM = REGISTRY.register("stem", () -> Stem.CODEC);
    public static final DeferredHolder<MapCodec<? extends SecretRealmGeneration>, MapCodec<Flat>> FLAT = REGISTRY.register("flat", () -> Flat.CODEC);
    public static final DeferredHolder<MapCodec<? extends SecretRealmGeneration>, MapCodec<SecretRealmGeneration.Void>> VOID = REGISTRY.register("void", () -> SecretRealmGeneration.Void.CODEC);
    public static final DeferredHolder<MapCodec<? extends SecretRealmGeneration>, MapCodec<Template>> TEMPLATE = REGISTRY.register("template", () -> Template.CODEC);
    public static final DeferredHolder<MapCodec<? extends SecretRealmGeneration>, MapCodec<Existing>> EXISTING = REGISTRY.register("existing", () -> Existing.CODEC);
}
