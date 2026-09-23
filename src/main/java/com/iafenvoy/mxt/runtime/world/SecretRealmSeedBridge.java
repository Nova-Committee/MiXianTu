package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.mixin.LevelResourceAccessor;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.util.OptionalLong;

/**
 * Hands a per-instance seed to a level while it is being constructed. Chunk generators are seeded while the
 * datapack registry is built, so a runtime dimension would normally reuse the world seed; the instance's own
 * seed is published here for the narrow window in which {@code ServerLevel} is constructed.
 */
public final class SecretRealmSeedBridge {
    private static ResourceKey<Level> constructing;
    private static long seed;

    private SecretRealmSeedBridge() {
    }

    public static void begin(ResourceKey<Level> dimension, long value) {
        constructing = dimension;
        seed = value;
    }

    public static void end() {
        constructing = null;
        seed = 0L;
    }

    /**
     * The seed of the level currently being constructed, or empty when the level is not a secret realm.
     */
    public static OptionalLong pending() {
        return constructing == null ? OptionalLong.empty() : OptionalLong.of(seed);
    }

    /**
     * The world-relative folder of a dimension, the layout vanilla uses for level data.
     */
    public static LevelResource folder(Identifier dimension) {
        return LevelResourceAccessor.mxt$newInstance("dimensions/%s/%s".formatted(dimension.getNamespace(), dimension.getPath()));
    }

    /**
     * The world-relative folder every dimension lives in, used to find instance folders nothing claims.
     */
    public static LevelResource root() {
        return LevelResourceAccessor.mxt$newInstance("dimensions");
    }
}
