package com.iafenvoy.mxt.compat.kubejs.codec;

import com.google.gson.JsonElement;
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

    // One decode per distinct JSON text, because decoding builds the whole provider tree. Every cache is dropped
    // when the registries change (a decoded value can hold holders of its world), and each codec keeps at most
    // {@value #CACHE_LIMIT} entries.
    @SuppressWarnings("unchecked")
    public static <T> T decodeCached(Codec<T> codec, JsonElement json, RegistryAccess registries) {
        dropCachesWhenRegistriesChange(registries);
        Map<String, Object> cache = CACHES.computeIfAbsent(codec, ignored -> new ConcurrentHashMap<>());
        if (cache.size() >= CACHE_LIMIT) cache.clear();
        return (T) cache.computeIfAbsent(json.toString(), ignored -> parse(codec, json, registries));
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
