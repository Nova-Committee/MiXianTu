package com.iafenvoy.mxt.data.trigger.builtin;

import com.iafenvoy.mxt.data.trigger.Trigger;
import com.iafenvoy.mxt.data.trigger.TriggerSignal;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

/**
 * Built-in signals carry no additional JSON fields; the registry entry supplies the signal identifier.
 */
public record BuiltinTrigger(Identifier signalType) implements Trigger {
    @Override
    public MapCodec<BuiltinTrigger> codec() {
        return MapCodec.unit(this);
    }

    @Override
    public boolean matches(TriggerSignal signal) {
        return this.signalType.equals(signal.type());
    }
}
