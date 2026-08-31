package com.iafenvoy.mxt.runtime.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import net.minecraft.core.Holder;

import java.util.Map;

/**
 * Mutable-free serializable state for one independently stored resource pool.
 */
public record AuraPool(double amount, double maximum, double regenPerTick) {
    public static final Codec<AuraPool> CODEC = RecordCodecBuilder.<AuraPool>create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("amount", 0.0D).forGetter(AuraPool::amount),
            Codec.DOUBLE.optionalFieldOf("maximum", 0.0D).forGetter(AuraPool::maximum),
            Codec.DOUBLE.optionalFieldOf("regen_per_tick", 0.0D).forGetter(AuraPool::regenPerTick)
    ).apply(i, AuraPool::new)).validate(AuraPool::validate);
    public static final Codec<Map<Holder<Resource>, AuraPool>> GROUPED_CODEC = CollectionCodecs.map(Resource.CODEC, CODEC);

    public AuraPool {
        if (!Double.isFinite(amount) || amount < 0.0D || (!Double.isFinite(maximum) && maximum != Double.POSITIVE_INFINITY)
                || maximum < 0.0D || !Double.isFinite(regenPerTick)) {
            throw new IllegalArgumentException("Aura pool values must be finite and non-negative, except an unlimited maximum");
        }
        if (Double.isFinite(maximum) && amount > maximum) amount = maximum;
    }

    public AuraPool change(double delta) {
        return new AuraPool(Math.max(0.0D, this.amount + delta), this.maximum, this.regenPerTick);
    }

    public AuraPool withMaximum(double maximum) {
        return new AuraPool(this.amount, maximum, this.regenPerTick);
    }

    private static DataResult<AuraPool> validate(AuraPool pool) {
        try {
            new AuraPool(pool.amount, pool.maximum, pool.regenPerTick);
            return DataResult.success(pool);
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }
}
