package com.iafenvoy.mxt.data.storage.builtin;

import com.iafenvoy.mxt.data.ability.AbilityContext;
import com.iafenvoy.mxt.data.storage.DataStorage;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * A pool of uses. {@code maximum} and {@code recharge_ticks} are the declaration and {@code remaining} how many are
 * left; a pool that was never spent keeps no remaining, which reads as full, so a freshly granted host needs no
 * initialisation. The pool refills itself one step per interval while it is held.
 */
public final class ChargesDataStorage extends DataStorage {
    // Addressing instance for a value that does not come from a declaration.
    public static final ChargesDataStorage INSTANCE = new ChargesDataStorage(new Constant(0.0D), new Constant(0.0D), Optional.empty(), 0L);
    public static final MapCodec<ChargesDataStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("maximum").forGetter(ChargesDataStorage::maximum),
            NumberProvider.CODEC.fieldOf("recharge_ticks").forGetter(ChargesDataStorage::rechargeTicks),
            Codec.DOUBLE.optionalFieldOf("remaining").forGetter(ChargesDataStorage::remaining),
            Codec.LONG.optionalFieldOf("last_change", 0L).forGetter(ChargesDataStorage::lastChange)
    ).apply(i, ChargesDataStorage::new));
    private final NumberProvider maximum;
    private final NumberProvider rechargeTicks;
    private Optional<Double> remaining;
    private long lastChange;

    private ChargesDataStorage(NumberProvider maximum, NumberProvider rechargeTicks, Optional<Double> remaining, long lastChange) {
        this.maximum = maximum;
        this.rechargeTicks = rechargeTicks;
        this.remaining = remaining;
        this.lastChange = lastChange;
    }

    @Override
    public MapCodec<ChargesDataStorage> codec() {
        return CODEC;
    }

    @Override
    public DataStorage copy() {
        return new ChargesDataStorage(this.maximum, this.rechargeTicks, this.remaining, this.lastChange);
    }

    // The last write is the interval's anchor, and both spending and refilling make one, so this may run every tick
    // and still add at most one step per interval. A pool that is full or was never spent writes nothing.
    @Override
    public void tick(AbilityContext context) {
        double left = this.remaining.orElse(Double.NaN);
        if (!Double.isFinite(left)) return;
        double maximum = this.maximum.evaluate(context.formula());
        if (!Double.isFinite(maximum) || left >= maximum) return;
        double ticks = this.rechargeTicks.evaluate(context.formula());
        // An interval that is unusable never refills; zero would otherwise fill the pool on every tick.
        if (!Double.isFinite(ticks) || ticks <= 0.0D) return;
        if (context.gameTime() - this.lastChange < Math.max(1L, Math.round(ticks))) return;
        this.remaining = Optional.of(Math.min(maximum, left + 1.0D));
        this.lastChange = context.gameTime();
        this.markDirty();
    }

    public NumberProvider maximum() {
        return this.maximum;
    }

    public NumberProvider rechargeTicks() {
        return this.rechargeTicks;
    }

    public Optional<Double> remaining() {
        return this.remaining;
    }

    public long lastChange() {
        return this.lastChange;
    }

    public void setRemaining(double value, long gameTime) {
        this.remaining = Optional.of(value);
        this.lastChange = gameTime;
        this.markDirty();
    }

    // A pool written by content counts its refill interval from the write, exactly like one that was spent.
    @Override
    public void writtenAt(long gameTime) {
        this.lastChange = gameTime;
    }

    // The declaration half, which is the only part an ability states: how many uses the pool starts with and how
    // fast it refills. The remaining count is state.
    public record Settings(NumberProvider maximum, NumberProvider rechargeTicks) {
        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(i -> i.group(
                NumberProvider.CODEC.fieldOf("maximum").forGetter(Settings::maximum),
                NumberProvider.CODEC.fieldOf("recharge_ticks").forGetter(Settings::rechargeTicks)
        ).apply(i, Settings::new));

        public ChargesDataStorage declared() {
            return new ChargesDataStorage(this.maximum, this.rechargeTicks, Optional.empty(), 0L);
        }
    }
}
