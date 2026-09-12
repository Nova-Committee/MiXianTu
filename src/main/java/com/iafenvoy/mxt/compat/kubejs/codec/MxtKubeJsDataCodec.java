package com.iafenvoy.mxt.compat.kubejs.codec;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decodes KubeJS JSON through the same registry-aware codecs used by datapacks.
 */
public final class MxtKubeJsDataCodec {
    private static final int CACHE_LIMIT = 256;
    private static final Map<Codec<?>, Map<String, Object>> CACHES = new ConcurrentHashMap<>();
    private static volatile RegistryAccess cachedRegistries;

    private MxtKubeJsDataCodec() {
    }

    public static <T> T decode(Codec<T> codec, JsonElement json, RegistryAccess registries) {
        return parse(codec, json, registries);
    }

    /**
     * Decodes a definition once per distinct JSON text. Decoding builds the whole provider tree,
     * including exp4j expressions, so a script that evaluates the same definition every tick would
     * otherwise pay for it every tick.
     *
     * <p>A decoded value can hold holders of the world it was read in, so every cache is dropped
     * as soon as the registries change, and each codec keeps at most {@value #CACHE_LIMIT} entries.</p>
     */
    @SuppressWarnings("unchecked")
    public static <T> T decodeCached(Codec<T> codec, JsonElement json, RegistryAccess registries) {
        if (!(json instanceof JsonObject object)) return parse(codec, json, registries);
        dropCachesWhenRegistriesChange(registries);
        Map<String, Object> cache = CACHES.computeIfAbsent(codec, ignored -> new ConcurrentHashMap<>());
        if (cache.size() >= CACHE_LIMIT) cache.clear();
        return (T) cache.computeIfAbsent(object.toString(), ignored -> parse(codec, json, registries));
    }

    private static <T> T parse(Codec<T> codec, JsonElement json, RegistryAccess registries) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        return codec.parse(ops, json).getOrThrow(error -> new IllegalArgumentException("Invalid MXT data: " + error));
    }

    private static void dropCachesWhenRegistriesChange(RegistryAccess registries) {
        if (registries == cachedRegistries) return;
        synchronized (MxtKubeJsDataCodec.class) {
            if (registries == cachedRegistries) return;
            cachedRegistries = registries;
            CACHES.clear();
        }
    }
}
