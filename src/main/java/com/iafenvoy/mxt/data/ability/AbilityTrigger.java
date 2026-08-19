package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.function.Function;

/**
 * Event selector. Event subscription remains centralized in the server trigger bridge.
 */
public sealed interface AbilityTrigger {
    Codec<AbilityTrigger> CODEC = MxtRegistries.ABILITY_TRIGGER_TYPE.byNameCodec().dispatch("type", AbilityTrigger::codec, Function.identity());

    String event();

    MapCodec<? extends AbilityTrigger> codec();

    record Builtin(String event, MapCodec<Builtin> codec) implements AbilityTrigger {
        public Builtin(String event) {
            this(event, MapCodec.unit(new Builtin(event, null)));
        }
    }
}
