package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraValue;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One independently stored aura pool. Mutable on purpose: a pool is read and written in place on every query and
 * every tick, so a copy is only made where a caller could otherwise write back into the stored stock
 * ({@link #copy()} / {@link #copyAll(Map)}).
 * <p>
 * {@code supplied} is how much of {@code amount} the block emitters at this position contribute, carried
 * because a consumer allowed to spend the ground it stands on has to know how much of what it sees is its own.
 */
public final class AuraPool {
    public static final Codec<AuraPool> CODEC = RecordCodecBuilder.<AuraPool>create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("amount", 0.0D).forGetter(AuraPool::amount),
            Codec.DOUBLE.optionalFieldOf("maximum", 0.0D).forGetter(AuraPool::maximum),
            Codec.DOUBLE.optionalFieldOf("regen_per_tick", 0.0D).forGetter(AuraPool::regenPerTick),
            Codec.DOUBLE.optionalFieldOf("supplied", 0.0D).forGetter(AuraPool::supplied)
    ).apply(i, AuraPool::new)).validate(AuraPool::validate);
    public static final StreamCodec<RegistryFriendlyByteBuf, AuraPool> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, AuraPool::amount,
            ByteBufCodecs.DOUBLE, AuraPool::maximum,
            ByteBufCodecs.DOUBLE, AuraPool::regenPerTick,
            ByteBufCodecs.DOUBLE, AuraPool::supplied,
            AuraPool::new);
    public static final Codec<Map<Holder<Aura>, AuraPool>> GROUPED_CODEC = CollectionCodecs.map(Aura.CODEC, CODEC);

    private double amount;
    private double maximum;
    private double regenPerTick;
    private double supplied;

    // Decoding rejects a bad value rather than clamping it; every mutation below clamps, so a pool that exists is
    // always usable.
    public AuraPool(double amount, double maximum, double regenPerTick, double supplied) {
        if (!Double.isFinite(amount) || amount < 0.0D || (!Double.isFinite(maximum) && maximum != Double.POSITIVE_INFINITY)
                || maximum < 0.0D || !Double.isFinite(regenPerTick) || !Double.isFinite(supplied) || supplied < 0.0D) {
            throw new IllegalArgumentException("Aura pool values must be finite and non-negative, except an unlimited maximum");
        }
        this.amount = Double.isFinite(maximum) ? Math.min(amount, maximum) : amount;
        this.maximum = maximum;
        this.regenPerTick = regenPerTick;
        this.supplied = supplied;
    }

    // A pool nothing but the environment feeds, which is every pool that is stored rather than resolved.
    public static AuraPool natural(double amount, double maximum, double regenPerTick) {
        return new AuraPool(amount, maximum, regenPerTick, 0.0D);
    }

    public static AuraPool empty() {
        return natural(0.0D, 0.0D, 0.0D);
    }

    public static Map<Holder<Aura>, AuraPool> copyAll(Map<Holder<Aura>, AuraPool> pools) {
        Map<Holder<Aura>, AuraPool> copies = new LinkedHashMap<>();
        pools.forEach((aura, pool) -> copies.put(aura, pool.copy()));
        return copies;
    }

    public double amount() {
        return this.amount;
    }

    public double maximum() {
        return this.maximum;
    }

    public double regenPerTick() {
        return this.regenPerTick;
    }

    public double supplied() {
        return this.supplied;
    }

    public AuraPool copy() {
        return new AuraPool(this.amount, this.maximum, this.regenPerTick, this.supplied);
    }

    public void set(double amount, double maximum, double regenPerTick, double supplied) {
        this.maximum = Math.max(0.0D, maximum);
        this.amount = clamp(amount, this.maximum);
        this.regenPerTick = Math.max(0.0D, regenPerTick);
        this.supplied = Math.max(0.0D, supplied);
    }

    public void change(double delta) {
        this.amount = clamp(this.amount + delta, this.maximum);
    }

    public void setMaximum(double maximum) {
        this.maximum = Math.max(0.0D, maximum);
        this.amount = clamp(this.amount, this.maximum);
    }

    // A ceiling handed in as a delta, which is how a zone's bonus and a block contribution's maximum both arrive.
    public void addMaximum(double delta) {
        if (this.maximum != Double.POSITIVE_INFINITY) this.setMaximum(this.maximum + delta);
    }

    public void setRegenPerTick(double regenPerTick) {
        this.regenPerTick = Math.max(0.0D, regenPerTick);
    }

    public void supply(double supplied) {
        this.supplied = Math.max(0.0D, supplied);
    }

    // A pool for an aura that had none: the same arithmetic as {@link #update}, from an empty start.
    public static AuraPool of(AuraValue previous, AuraValue current) {
        AuraPool pool = empty();
        pool.update(previous, current);
        return pool;
    }

    // The block part of this pool moving from one aggregate to another: the stock, the ceiling it was granted and
    // its regeneration all follow the difference.
    public void update(AuraValue previous, AuraValue current) {
        this.addMaximum(current.max().resolve(current.amount()) - previous.max().resolve(previous.amount()));
        this.change(current.amount() - previous.amount());
        this.setRegenPerTick(this.regenPerTick + current.regenPerTick() - previous.regenPerTick());
    }

    // Splits one chunk-sized pool across the sections that share it, so the zone's total is preserved.
    public AuraPool share(int sections) {
        return natural(this.amount / sections, this.maximum / sections, this.regenPerTick / sections);
    }

    private static double clamp(double amount, double maximum) {
        double floored = Math.max(0.0D, amount);
        return Double.isFinite(maximum) ? Math.min(floored, maximum) : floored;
    }

    private static DataResult<AuraPool> validate(AuraPool pool) {
        try {
            pool.copy();
            return DataResult.success(pool);
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }
}
