package com.iafenvoy.mxt.runtime.world;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.Map;

/**
 * Serializable state for one independently stored resource pool. {@code supplied} is how much of
 * {@code amount} the block emitters at this position are contributing, carried because a consumer allowed to
 * spend the ground it stands on has to know how much of what it sees is already its own.
 */
public record AuraPool(double amount, double maximum, double regenPerTick, double supplied) {
    public static final Codec<AuraPool> CODEC = RecordCodecBuilder.<AuraPool>create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("amount", 0.0D).forGetter(AuraPool::amount),
            Codec.DOUBLE.optionalFieldOf("maximum", 0.0D).forGetter(AuraPool::maximum),
            Codec.DOUBLE.optionalFieldOf("regen_per_tick", 0.0D).forGetter(AuraPool::regenPerTick),
            Codec.DOUBLE.optionalFieldOf("supplied", 0.0D).forGetter(AuraPool::supplied)
    ).apply(i, AuraPool::new)).validate(AuraPool::validate);
    public static final Codec<Map<Holder<Aura>, AuraPool>> GROUPED_CODEC = CollectionCodecs.map(Aura.CODEC, CODEC);

    public AuraPool {
        if (!Double.isFinite(amount) || amount < 0.0D || (!Double.isFinite(maximum) && maximum != Double.POSITIVE_INFINITY)
                || maximum < 0.0D || !Double.isFinite(regenPerTick) || !Double.isFinite(supplied) || supplied < 0.0D) {
            throw new IllegalArgumentException("Aura pool values must be finite and non-negative, except an unlimited maximum");
        }
        if (Double.isFinite(maximum) && amount > maximum) amount = maximum;
    }

    /**
     * A pool nothing but the environment feeds, which is every pool that is stored rather than resolved for
     * a position.
     */
    public static AuraPool natural(double amount, double maximum, double regenPerTick) {
        return new AuraPool(amount, maximum, regenPerTick, 0.0D);
    }

    public static AuraPool empty() {
        return natural(0.0D, 0.0D, 0.0D);
    }

    public AuraPool change(double delta) {
        return new AuraPool(Math.max(0.0D, this.amount + delta), this.maximum, this.regenPerTick, this.supplied);
    }

    public AuraPool withMaximum(double maximum) {
        return new AuraPool(this.amount, maximum, this.regenPerTick, this.supplied);
    }

    private static DataResult<AuraPool> validate(AuraPool pool) {
        try {
            new AuraPool(pool.amount, pool.maximum, pool.regenPerTick, pool.supplied);
            return DataResult.success(pool);
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }
}
