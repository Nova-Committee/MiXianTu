package com.iafenvoy.mxt.data.storage;

import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * How long a host stays on cooldown. {@code ticks} is the declared length and {@code duration} is the length the
 * last use actually got, which is what a display draws against; the tick it was written is when that cooldown
 * started.
 */
public record CooldownDataStorage(NumberProvider ticks, Optional<Double> duration) implements DataStorage {
    /**
     * Addressing instance for the write the runtime owes every host, whether or not the content declared one:
     * the value's class is the slot, so this instance stands in for a declaration nobody wrote.
     */
    public static final CooldownDataStorage INSTANCE = new CooldownDataStorage(new Constant(0.0D), Optional.empty());
    public static final MapCodec<CooldownDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("ticks").forGetter(CooldownDataStorage::ticks),
            Codec.DOUBLE.optionalFieldOf("duration").forGetter(CooldownDataStorage::duration)
    ).apply(i, CooldownDataStorage::new));

    @Override
    public MapCodec<CooldownDataStorage> codec() {
        return CODEC;
    }

    /**
     * This kind with the length a use got, keeping the declared parameters it carries.
     */
    public CooldownDataStorage withDuration(double value) {
        return new CooldownDataStorage(this.ticks, Optional.of(value));
    }
}