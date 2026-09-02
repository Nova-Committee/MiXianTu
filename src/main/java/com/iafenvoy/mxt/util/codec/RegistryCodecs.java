package com.iafenvoy.mxt.util.codec;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Codecs and helpers for fields that accept either a registry entry or a registry tag.
 */
public final class RegistryCodecs {
    private RegistryCodecs() {
    }

    /**
     * Accepts one registry entry id or one tag id prefixed with {@code #}.
     */
    public static <T> Codec<Either<Holder<T>, TagKey<T>>> holderOrTag(ResourceKey<Registry<T>> key) {
        return Codec.either(RegistryFixedCodec.create(key), TagKey.hashedCodec(key));
    }

    /**
     * Accepts one entry/tag value or a mixed array of entry and tag values.
     */
    public static <T> Codec<List<Either<Holder<T>, TagKey<T>>>> holderOrTagList(ResourceKey<Registry<T>> key) {
        return CombinedCodecs.combineCodec(holderOrTag(key));
    }

    /**
     * A delayed variant for registries which are not available while a datapack
     * registry is decoded, such as dimension stems.
     */
    public static <T> Codec<List<Either<ResourceKey<T>, TagKey<T>>>> keyOrTagList(ResourceKey<Registry<T>> key) {
        return CombinedCodecs.combineCodec(Codec.either(ResourceKey.codec(key), TagKey.hashedCodec(key)));
    }

    public static <T> boolean matches(Collection<Either<Holder<T>, TagKey<T>>> values, Holder<T> candidate) {
        return values.stream().anyMatch(value -> value.map(entry -> entry.value() == candidate.value(), candidate::is));
    }

    public static <T> boolean matches(Collection<Either<Holder<T>, TagKey<T>>> values, Registry<T> registry,
                                      ResourceKey<Registry<T>> key, Identifier candidate) {
        return registry.get(ResourceKey.create(key, candidate)).map(holder -> matches(values, holder)).orElse(false);
    }

    public static <T> boolean matchesKey(Collection<Either<ResourceKey<T>, TagKey<T>>> values, Registry<T> registry,
                                         Identifier candidate) {
        return values.stream().anyMatch(value -> value.map(key -> key.identifier().equals(candidate),
                tag -> registry.listElements().anyMatch(holder -> holder.is(tag) && holder.unwrapKey()
                        .map(key -> key.identifier().equals(candidate)).orElse(false))));
    }

    /**
     * Matches direct registry keys without resolving tags. This is used by client-side code for
     * registries such as dimension stems, which are intentionally unavailable in client registry access.
     */
    public static <T> boolean matchesKey(Collection<Either<ResourceKey<T>, TagKey<T>>> values, Identifier candidate) {
        return values.stream().anyMatch(value -> value.left().map(key -> key.identifier().equals(candidate)).orElse(false));
    }

    /**
     * Expands direct holders and tags to their current registry entries.
     */
    public static <T> Stream<Holder<T>> resolve(Collection<Either<Holder<T>, TagKey<T>>> values, Registry<T> registry) {
        return values.stream().flatMap(value -> value.map(Stream::of,
                tag -> registry.listElements().filter(holder -> holder.is(tag))));
    }

    public static <T> List<Holder<T>> listAll(Collection<Either<Holder<T>, TagKey<T>>> values, Registry<T> registry) {
        return resolve(values, registry).toList();
    }
}
