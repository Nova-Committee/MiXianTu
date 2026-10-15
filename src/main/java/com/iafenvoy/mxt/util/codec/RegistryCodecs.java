package com.iafenvoy.mxt.util.codec;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.core.HolderSet;
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

    public static <T> Codec<Either<Holder<T>, TagKey<T>>> holderOrTag(ResourceKey<Registry<T>> key) {
        return Codec.either(RegistryFixedCodec.create(key), TagKey.hashedCodec(key));
    }

    public static <T> Codec<List<Either<Holder<T>, TagKey<T>>>> holderOrTagList(ResourceKey<Registry<T>> key) {
        return MiscCodecs.combineCodec(holderOrTag(key));
    }

    public static <T> boolean matches(Collection<Either<Holder<T>, TagKey<T>>> values, Registry<T> registry, Identifier candidate) {
        return registry.get(candidate).map(holder -> matches(values, holder)).orElse(false);
    }

    public static <T> boolean matches(Collection<Either<Holder<T>, TagKey<T>>> values, Registry<T> registry, T candidate) {
        return matches(values, registry.wrapAsHolder(candidate));
    }

    public static <T> boolean matches(Collection<Either<Holder<T>, TagKey<T>>> values, Holder<T> candidate) {
        return values.stream().anyMatch(value -> value.map(candidate::equals, candidate::is));
    }

    public static <T> Stream<Holder<T>> resolve(Collection<Either<Holder<T>, TagKey<T>>> values, Provider access, ResourceKey<? extends Registry<T>> key) {
        return resolve(values, access.lookupOrThrow(key));
    }

    public static <T> Stream<Holder<T>> resolve(Collection<Either<Holder<T>, TagKey<T>>> values, RegistryLookup<T> registry) {
        return values.stream().flatMap(value -> value.map(Stream::of, tag -> registry.get(tag).map(HolderSet.ListBacked::stream).orElse(Stream.empty()))).distinct();
    }
}
