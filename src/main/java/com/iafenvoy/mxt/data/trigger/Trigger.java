package com.iafenvoy.mxt.data.trigger;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.function.Function;

/**
 * Data-driven matcher for a runtime {@link TriggerSignal}.
 */
public interface Trigger {
    Codec<Trigger> CODEC = MxtRegistries.TRIGGER_TYPE.byNameCodec()
            .dispatch("type", Trigger::codec, Function.identity());

    MapCodec<? extends Trigger> codec();

    default boolean matches(TriggerSignal signal) {
        return true;
    }

    Identifier signalType();

    /**
     * Built-in signals carry no additional JSON fields. The registry entry
     * supplies the signal identifier, keeping the data format extensible.
     */
    record Builtin(Identifier signalType) implements Trigger {
        @Override
        public MapCodec<Builtin> codec() {
            return MapCodec.unit(this);
        }

        @Override
        public boolean matches(TriggerSignal signal) {
            return this.signalType.equals(signal.type());
        }
    }
}
