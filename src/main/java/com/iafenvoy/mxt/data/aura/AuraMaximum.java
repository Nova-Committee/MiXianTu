package com.iafenvoy.mxt.data.aura;

import com.iafenvoy.mxt.data.aura.builtin.FixedAuraMaximum;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.function.Function;

/**
 * Resolves the environmental storage limit for one aura chunk; block contributions and formation bonuses are
 * applied separately at runtime.
 */
public interface AuraMaximum {
    Codec<AuraMaximum> TYPED_CODEC = MxtRegistries.AURA_MAXIMUM_TYPE.byNameCodec()
            .dispatch("type", AuraMaximum::codec, Function.identity());
    // A non-negative number is shorthand for {"type":"mxt:fixed","value":...}.
    Codec<AuraMaximum> CODEC = Codec.either(Codec.doubleRange(0.0D, Double.MAX_VALUE), TYPED_CODEC).xmap(
            value -> value.map(FixedAuraMaximum::new, Function.identity()),
            value -> value instanceof FixedAuraMaximum(double value1) ? Either.left(value1) : Either.right(value));

    double resolve(double initialAura);

    MapCodec<? extends AuraMaximum> codec();
}
