package com.iafenvoy.mxt.data.storage.builtin;

import com.iafenvoy.mxt.data.storage.DataStorage;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * A pool of uses. {@code maximum} and {@code recharge_ticks} are the declaration and {@code remaining} how many are
 * left; a value that was never written keeps no remaining, which reads as full, so a freshly granted host needs no
 * initialisation.
 */
public record ChargesDataStorage(NumberProvider maximum, NumberProvider rechargeTicks,
                                 Optional<Double> remaining) implements DataStorage {
    // Addressing instance for a write that does not come from a declaration.
    public static final ChargesDataStorage INSTANCE = new ChargesDataStorage(new Constant(0.0D), new Constant(0.0D), Optional.empty());
    public static final MapCodec<ChargesDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("maximum").forGetter(ChargesDataStorage::maximum),
            NumberProvider.CODEC.fieldOf("recharge_ticks").forGetter(ChargesDataStorage::rechargeTicks),
            Codec.DOUBLE.optionalFieldOf("remaining").forGetter(ChargesDataStorage::remaining)
    ).apply(i, ChargesDataStorage::new));

    @Override
    public MapCodec<ChargesDataStorage> codec() {
        return CODEC;
    }

    public ChargesDataStorage withRemaining(double value) {
        return new ChargesDataStorage(this.maximum, this.rechargeTicks, Optional.of(value));
    }
}