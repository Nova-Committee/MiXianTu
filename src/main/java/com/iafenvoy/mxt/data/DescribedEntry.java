package com.iafenvoy.mxt.data;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * A typed value with an optional translation key. It works for every condition and action family because the
 * JSON key used inside the described object is supplied by the caller; a value without a description is encoded
 * as the value itself.
 */
public record DescribedEntry<T>(T value, Optional<String> description) {
    public DescribedEntry(T value) {
        this(value, Optional.empty());
    }

    /** Accepts a bare value or {@code {"<key>": value, "description": translation}}. */
    public static <T> Codec<DescribedEntry<T>> codec(Codec<T> valueCodec, String key) {
        Codec<DescribedEntry<T>> described = RecordCodecBuilder.create(i -> i.group(
                valueCodec.fieldOf(key).forGetter(DescribedEntry::value),
                Codec.STRING.optionalFieldOf("description").forGetter(DescribedEntry::description)
        ).apply(i, DescribedEntry::new));
        return Codec.either(valueCodec, described).xmap(
                value -> value.map(DescribedEntry::new, entry -> entry),
                entry -> entry.description().isPresent() ? Either.right(entry) : Either.left(entry.value()));
    }
}
