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
 *
 * <p>The {@code keyOrTag} pair is for registries a pack cannot name a holder of: {@code minecraft:dimension} is
 * loaded in the layer after the one the mod's own registries are loaded in, so a {@code Holder<LevelStem>} has no
 * registry to resolve against while a pack decodes - an id has no such need and is resolved where it is read.
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

    public static <T> Codec<Either<ResourceKey<T>, TagKey<T>>> keyOrTag(ResourceKey<Registry<T>> key) {
        return Codec.either(ResourceKey.codec(key), TagKey.hashedCodec(key));
    }

    public static <T> Codec<List<Either<ResourceKey<T>, TagKey<T>>>> keyOrTagList(ResourceKey<Registry<T>> key) {
        return MiscCodecs.combineCodec(keyOrTag(key));
    }

    public static <T> boolean matches(Collection<Either<Holder<T>, TagKey<T>>> values, Registry<T> registry, Identifier candidate) {
        return registry.get(candidate).map(holder -> matches(values, holder)).orElse(false);
    }

    /**
     * The same question for a field written as ids: an id matches by itself, and a tag is expanded through the
     * registry, which is only asked here - never while a pack loads.
     */
    public static <T> boolean matchesKeys(Collection<Either<ResourceKey<T>, TagKey<T>>> values, Registry<T> registry,
                                          Identifier candidate) {
        return values.stream().anyMatch(value -> value.map(
                key -> key.identifier().equals(candidate),
                tag -> registry.get(tag).flatMap(set -> registry.get(candidate).map(set::contains)).orElse(false)));
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
