package com.iafenvoy.mxt.runtime.world;

import com.google.common.collect.ImmutableList;
import com.iafenvoy.mxt.mixin.MinecraftServerRuntimeAccessor;
import com.iafenvoy.mxt.runtime.MxtProfilerSources;
import com.iafenvoy.saturn.profiling.ProfilerScope;
import com.iafenvoy.saturn.profiling.ServerProfiling;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.*;

/**
 * Optional runtime dimension loader operating on already decoded LevelStem values. It never mutates the dynamic
 * dimension registry, so callers can keep it as a fallback for secret realms.
 */
public final class RuntimeDimensionService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<ServerLevel> OWNED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<ResourceKey<Level>, ServerLevel> CLOSING = new HashMap<>();

    static boolean owns(ServerLevel level) {
        return OWNED.contains(level);
    }

    static void clear() {
        OWNED.clear();
        CLOSING.clear();
    }

    private RuntimeDimensionService() {
    }

    @SuppressWarnings("deprecation")
    public static Optional<ServerLevel> load(MinecraftServer server, ResourceKey<Level> key, LevelStem stem) {
        try (ProfilerScope _ = ServerProfiling.scope(MxtProfilerSources.SECRET_REALM)) {
            Map<ResourceKey<Level>, ServerLevel> worlds = server.forgeGetWorldMap();
            synchronized (worlds) {
                ServerLevel existing = worlds.get(key);
                if (existing != null) return owns(existing) ? Optional.of(existing) : Optional.empty();
                if (CLOSING.containsKey(key) || !SecretRealmGenerationService.managed(server, key))
                    return Optional.empty();
                try {
                    WorldData worldData = server.getWorldData();
                    ServerLevelData properties = new DerivedLevelData(worldData, worldData.overworldData());
                    // Only the fallback: a stem carrying neoforge:seed_override replaces this inside the constructor.
                    long biomeSeed = BiomeManager.obfuscateSeed(server.overworld().getSeed());
                    ServerLevel level = new ServerLevel(server,
                            ((MinecraftServerRuntimeAccessor) server).mxt$executor(),
                            ((MinecraftServerRuntimeAccessor) server).mxt$storageSource(),
                            properties, key, stem, worldData.isDebugWorld(), biomeSeed,
                            ImmutableList.of(), false);
                    worlds.put(key, level);
                    OWNED.add(level);
                    server.markWorldsDirty();
                    return Optional.of(level);
                } catch (Exception exception) {
                    LOGGER.error("Failed to load runtime dimension {}", key.identifier(), exception);
                    return Optional.empty();
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    public static boolean unload(MinecraftServer server, ResourceKey<Level> key) {
        return unload(server, key, false);
    }

    private static boolean unload(MinecraftServer server, ResourceKey<Level> key, boolean discard) {
        try (ProfilerScope _ = ServerProfiling.scope(MxtProfilerSources.SECRET_REALM)) {
            if (!SecretRealmGenerationService.managed(server, key)) return false;
            Map<ResourceKey<Level>, ServerLevel> worlds = server.forgeGetWorldMap();
            synchronized (worlds) {
                ServerLevel pending = CLOSING.get(key);
                ServerLevel live = worlds.get(key);
                if (live != null) {
                    if (pending != null || !owns(live) || !live.players().isEmpty()) return false;
                    if (!discard) {
                        try {
                            live.save(null, true, live.noSave);
                        } catch (RuntimeException exception) {
                            LOGGER.warn("Failed to save runtime dimension {}", key.identifier(), exception);
                            return false;
                        }
                    }
                    if (discard) live.noSave = true;
                    CLOSING.put(key, live);
                    worlds.remove(key);
                    server.markWorldsDirty();
                    pending = live;
                }
                if (pending == null) return true;
                if (discard) pending.noSave = true;
                try {
                    if (live != null) NeoForge.EVENT_BUS.post(new LevelEvent.Unload(live));
                    pending.close();
                } catch (IOException | RuntimeException exception) {
                    LOGGER.warn("Failed to close runtime dimension {}", key.identifier(), exception);
                    return false;
                }
                CLOSING.remove(key);
                OWNED.remove(pending);
                return true;
            }
        }
    }

    public static boolean delete(MinecraftServer server, ResourceKey<Level> key) {
        return unload(server, key, true) && SecretRealmGenerationService.clearData(server, key);
    }
}
