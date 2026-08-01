package com.iafenvoy.mxt.util.codec;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import java.util.Set;

/**
 * Rejects structurally forbidden fields while leaving all other field handling to the wrapped codec.
 */
public final class ForbiddenFieldsCodec<T> implements Codec<T> {
    private final Codec<T> delegate;
    private final Set<String> fields;

    public static <T> Codec<T> reject(Codec<T> delegate, String... fields) {
        return new ForbiddenFieldsCodec<>(delegate, Set.of(fields));
    }

    private ForbiddenFieldsCodec(Codec<T> delegate, Set<String> fields) {
        this.delegate = delegate;
        this.fields = fields;
    }

    @Override
    public <A> DataResult<Pair<T, A>> decode(DynamicOps<A> ops, A input) {
        return ops.getMap(input).flatMap(map -> {
            for (String field : this.fields) {
                if (map.get(field) != null) {
                    return DataResult.error(() -> "Field '" + field + "' is not allowed here");
                }
            }
            return this.delegate.decode(ops, input);
        });
    }

    @Override
    public <A> DataResult<A> encode(T input, DynamicOps<A> ops, A prefix) {
        return this.delegate.encode(input, ops, prefix);
    }
}
