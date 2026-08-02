package com.iafenvoy.mxt.util.codec;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;

import java.util.List;

//These codec can recognize both singleton and array
public final class CombinedCodecs {
    public static <T> Codec<List<T>> combineCodec(Codec<T> codec) {
        return Codec.either(codec, AutoIgnoreListCodec.create(codec)).xmap(x -> x.map(List::of, l -> l), l -> l.size() == 1 ? Either.left(l.getFirst()) : Either.right(l));
    }
}
