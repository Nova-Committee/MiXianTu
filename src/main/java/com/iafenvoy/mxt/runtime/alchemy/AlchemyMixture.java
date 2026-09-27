package com.iafenvoy.mxt.runtime.alchemy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Optional;

/**
 * Quantity-weighted mixture. {@code balance} is 0 when {@code weight} is 0; it is never NaN.
 */
public record AlchemyMixture(Map<Identifier, Double> main, Map<Identifier, Double> auxiliary, double catalyst,
                             double balance, double weight, Optional<AlchemyFailure> inputFailure) {
    public static final Codec<AlchemyMixture> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE).fieldOf("main").forGetter(AlchemyMixture::main),
            Codec.unboundedMap(Identifier.CODEC, Codec.DOUBLE).fieldOf("auxiliary").forGetter(AlchemyMixture::auxiliary),
            Codec.DOUBLE.fieldOf("catalyst").forGetter(AlchemyMixture::catalyst),
            Codec.DOUBLE.fieldOf("balance").forGetter(AlchemyMixture::balance),
            Codec.DOUBLE.fieldOf("weight").forGetter(AlchemyMixture::weight),
            AlchemyFailure.CODEC.lenientOptionalFieldOf("input_failure").forGetter(AlchemyMixture::inputFailure)
    ).apply(i, AlchemyMixture::new));
    public static final AlchemyMixture EMPTY = new AlchemyMixture(Map.of(), Map.of(), 0.0D, 0.0D, 0.0D, Optional.empty());

    public AlchemyMixture {
        if (!Double.isFinite(balance)) balance = 0.0D;
        if (!Double.isFinite(catalyst) || catalyst < 0.0D) catalyst = 0.0D;
        if (!Double.isFinite(weight) || weight < 0.0D) weight = 0.0D;
    }

    public boolean defined() {
        return this.inputFailure.isEmpty() && this.weight > 0.0D;
    }
}
