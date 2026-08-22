package com.iafenvoy.mxt.compat.kubejs.codec;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;

/**
 * Shared codecs for the small JSON parameter objects passed to KubeJS callbacks.
 */
public final class MxtJsCodecs {
    public static final Codec<JsonObject> PARAMS = Codec.PASSTHROUGH.xmap(
            value -> value.convert(JsonOps.INSTANCE).getValue().getAsJsonObject(),
            value -> new Dynamic<>(JsonOps.INSTANCE, value)
    );

    private MxtJsCodecs() {
    }
}
