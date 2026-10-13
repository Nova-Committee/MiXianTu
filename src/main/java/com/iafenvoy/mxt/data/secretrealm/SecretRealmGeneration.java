package com.iafenvoy.mxt.data.secretrealm;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.function.Function;

/**
 * How a secret realm dimension is produced; the one required section of a secret realm definition. Every
 * registry reference is resolved when an instance is created rather than at decode: datapack registries load in
 * parallel, so a holder read at decode time may still be unbound.
 * <p>
 * Not sealed: the strategies live in {@code builtin}, and a permitted subtype would have to share this package.
 */
public interface SecretRealmGeneration {
    Codec<SecretRealmGeneration> CODEC = MxtRegistries.SECRET_REALM_GENERATION_TYPE.byNameCodec().dispatch("type", SecretRealmGeneration::codec, Function.identity());

    MapCodec<? extends SecretRealmGeneration> codec();
}
