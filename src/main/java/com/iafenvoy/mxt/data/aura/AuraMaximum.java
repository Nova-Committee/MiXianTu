package com.iafenvoy.mxt.data.aura;

import com.iafenvoy.mxt.data.aura.AuraMaximum.Fixed;
import com.iafenvoy.mxt.data.aura.AuraMaximum.InitialMultiplier;
import com.iafenvoy.mxt.data.aura.AuraMaximum.Unlimited;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.datafixers.util.Either;

import java.util.function.Function;

/**
 * Resolves the environmental storage limit for one aura chunk.
 * Block contributions and formation bonuses are applied separately at runtime.
 */
public sealed interface AuraMaximum permits Fixed, InitialMultiplier, Unlimited {
    Codec<AuraMaximum> TYPED_CODEC = MxtRegistries.AURA_MAXIMUM_TYPE.byNameCodec()
            .dispatch("type", AuraMaximum::codec, Function.identity());
    /**
     * A non-negative number is shorthand for {@code {"type":"mxt:fixed","value":...}}.
     */
    Codec<AuraMaximum> CODEC = Codec.either(Codec.doubleRange(0.0D, Double.MAX_VALUE), TYPED_CODEC).xmap(
            value -> value.map(Fixed::new, Function.identity()),
            value -> value instanceof Fixed(double value1) ? Either.left(value1) : Either.right(value));

    double resolve(double initialAura);

    MapCodec<? extends AuraMaximum> codec();

    record Fixed(double value) implements AuraMaximum {
        public static final MapCodec<Fixed> CODEC = Codec.doubleRange(0.0D, Double.MAX_VALUE)
                .fieldOf("value").xmap(Fixed::new, Fixed::value);

        @Override
        public double resolve(double initialAura) {
            return this.value;
        }

        @Override
        public MapCodec<Fixed> codec() {
            return CODEC;
        }
    }

    record InitialMultiplier(double multiplier) implements AuraMaximum {
        public static final InitialMultiplier ONE = new InitialMultiplier(1.0D);
        public static final MapCodec<InitialMultiplier> CODEC = Codec.doubleRange(0.0D, Double.MAX_VALUE)
                .optionalFieldOf("multiplier", 1.0D).xmap(InitialMultiplier::new, InitialMultiplier::multiplier);

        @Override
        public double resolve(double initialAura) {
            return Math.max(0.0D, initialAura) * this.multiplier;
        }

        @Override
        public MapCodec<InitialMultiplier> codec() {
            return CODEC;
        }
    }

    enum Unlimited implements AuraMaximum {
        INSTANCE;
        public static final MapCodec<Unlimited> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public double resolve(double initialAura) {
            return Double.POSITIVE_INFINITY;
        }

        @Override
        public MapCodec<Unlimited> codec() {
            return CODEC;
        }
    }
}
