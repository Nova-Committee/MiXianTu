package com.iafenvoy.mxt.data.trigger;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsTriggerMatchers;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtJsCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * A trigger matcher whose decision is a server script callback.
 *
 * <p>The matcher declares the signal it listens to, because the dispatcher indexes subscriptions by
 * signal; the callback then decides whether the concrete signal fires. This lets a data pack wait
 * for a custom signal a script publishes with {@code MxtTriggers.publish}.</p>
 */
public record JsTrigger(Identifier signal, String id, JsonObject params) implements Trigger {
    public static final MapCodec<JsTrigger> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("signal").forGetter(JsTrigger::signal),
            Codec.STRING.fieldOf("id").forGetter(JsTrigger::id),
            MxtJsCodecs.PARAMS.optionalFieldOf("params", new JsonObject()).forGetter(JsTrigger::params)
    ).apply(i, JsTrigger::new));

    @Override
    public Identifier signalType() {
        return this.signal;
    }

    @Override
    public boolean matches(TriggerSignal signal) {
        return this.signal.equals(signal.type()) && MxtJsTriggerMatchers.matches(this.id, signal, this.params);
    }

    @Override
    public MapCodec<JsTrigger> codec() {
        return CODEC;
    }
}
