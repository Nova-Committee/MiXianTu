package com.iafenvoy.mxt.util.codec;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

import java.util.Collection;

/**
 * A reading that refuses keys a definition must not carry.
 *
 * <p>{@code RecordCodecBuilder} reads the fields it was told about and silently drops every other key, so a
 * field written into the wrong kind of definition leaves no trace at all: a physique carrying an
 * {@code element} reads exactly like one carrying nothing. The pack author then sees a definition that says
 * what they meant while the game does something else, which is the one failure mode a load-time error exists
 * to prevent.</p>
 *
 * <p>This is therefore the missing half of a definition that is defined by what it is <em>not</em> allowed to
 * say - a physique is element-independent, and a spirit root binds an element - rather than a general
 * strictness pass over every field: only the keys that would make one definition mean another are listed.</p>
 */
public final class DefinitionCodecs {
    private DefinitionCodecs() {
    }

    /**
     * Refuses the named keys before the wrapped codec is asked to read them.
     *
     * @param codec   the reading the definition normally uses
     * @param what    what is being read, so the message can name the definition instead of a class
     * @param reason  why those keys are refused, appended to the message
     * @param refused the keys this definition must never declare
     */
    public static <A> MapCodec<A> refuseKeys(MapCodec<A> codec, String what, String reason, Collection<String> refused) {
        return codec.mapResult(new MapCodec.ResultFunction<A>() {
            @Override
            public <T> DataResult<A> apply(DynamicOps<T> ops, MapLike<T> input, DataResult<A> result) {
                for (String key : refused)
                    if (input.get(key) != null)
                        return DataResult.error(() -> what + " must not declare '" + key + "': " + reason);
                return result;
            }

            /**
             * Nothing is checked while writing: this codec only reads other people's files, and the values it
             * encodes are the ones it just decoded.
             */
            @Override
            public <T> RecordBuilder<T> coApply(DynamicOps<T> ops, A input, RecordBuilder<T> prefix) {
                return prefix;
            }
        });
    }
}
