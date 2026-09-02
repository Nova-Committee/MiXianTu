package com.iafenvoy.mxt.compat.kubejs.codec;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;

/**
 * Decodes KubeJS JSON through the same registry-aware codecs used by datapacks.
 */
public final class MxtKubeJsDataCodec {
    private MxtKubeJsDataCodec() {
    }

    public static <T> T decode(Codec<T> codec, JsonElement json, RegistryAccess registries) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        return codec.parse(ops, json).getOrThrow(error -> new IllegalArgumentException("Invalid MXT data: " + error));
    }
}
