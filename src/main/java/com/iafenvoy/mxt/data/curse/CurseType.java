package com.iafenvoy.mxt.data.curse;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.OptionalLong;
import java.util.function.Function;

/**
 * Code-owned lifecycle policy selected by a curse definition's {@code type}.
 */
public interface CurseType {
    Codec<CurseType> CODEC = MxtRegistries.CURSE_TYPE.byNameCodec().dispatch("type", CurseType::codec, Function.identity());
    MapCodec<CurseType> MAP_CODEC = MapCodec.assumeMapUnsafe(CODEC);

    Identifier id();

    MapCodec<? extends CurseType> codec();

    // Empty means the resolution cannot be applied at all. Callers reject the application instead of throwing, so
    // one malformed definition can never break a tick or an event handler.
    OptionalLong expiry(long duration, long gameTime);

    default boolean inert() {
        return false;
    }
}
