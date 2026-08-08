package com.iafenvoy.mxt.runtime.world;

import com.google.common.collect.ImmutableList;
import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.mixin.MinecraftServerRuntimeAccessor;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * Optional runtime dimension loader adapted from ResourceWorld for NeoForge 26.
 * It operates on already decoded LevelStem values and never mutates the dynamic
 * dimension registry, so callers can keep it as a fallback for realm instances.
 */
public final class RuntimeDimensionService {
    private RuntimeDimensionService() {
    }

    @SuppressWarnings("deprecation")
    public static Optional<ServerLevel> load(MinecraftServer server, ResourceKey<Level> key, LevelStem stem) {
        Map<ResourceKey<Level>, ServerLevel> worlds = server.forgeGetWorldMap();
        synchronized (worlds) {
            ServerLevel existing = worlds.get(key);
            if (existing != null) return Optional.of(existing);
            try {
                WorldData worldData = server.getWorldData();
                ServerLevelData properties = new DerivedLevelData(worldData, worldData.overworldData());
                long biomeSeed = BiomeManager.obfuscateSeed(server.overworld().getSeed());
                ServerLevel level = new ServerLevel(server,
                        ((MinecraftServerRuntimeAccessor) server).mxt$executor(),
                        ((MinecraftServerRuntimeAccessor) server).mxt$storageSource(),
                        properties, key, stem, worldData.isDebugWorld(), biomeSeed,
                        ImmutableList.of(), false);
                worlds.put(key, level);
                server.markWorldsDirty();
                return Optional.of(level);
            } catch (Exception exception) {
                MiXianTu.LOGGER.error("Failed to load runtime dimension {}", key.identifier(), exception);
                return Optional.empty();
            }
        }
    }

    public static Optional<ServerLevel> load(MinecraftServer server, ResourceKey<Level> key,
                                             ResourceKey<LevelStem> stemKey) {
        Registry<LevelStem> stems = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
        return stems.get(stemKey).flatMap(holder -> load(server, key, holder.value()));
    }

    /**
     * Removes a runtime level after its players have been moved elsewhere. The
     * overworld and dimensions with players are deliberately never removed.
     */
    @SuppressWarnings("deprecation")
    public static boolean unload(MinecraftServer server, ResourceKey<Level> key) {
        if (Level.OVERWORLD.equals(key)) return false;
        Map<ResourceKey<Level>, ServerLevel> worlds = server.forgeGetWorldMap();
        synchronized (worlds) {
            ServerLevel level = worlds.get(key);
            if (level == null || !level.players().isEmpty()) return false;
            worlds.remove(key);
            server.markWorldsDirty();
            try {
                level.close();
            } catch (IOException exception) {
                MiXianTu.LOGGER.warn("Failed to close runtime dimension {}", key.identifier(), exception);
            }
            return true;
        }
    }
}
