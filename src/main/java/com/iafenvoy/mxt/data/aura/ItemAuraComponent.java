package com.iafenvoy.mxt.data.aura;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** Persistent remaining aura stored on one partially consumed item stack. */
public record ItemAuraComponent(double remain) {
    public static final Codec<ItemAuraComponent> CODEC = Codec.DOUBLE.comapFlatMap(
            value -> Double.isFinite(value) && value >= 0.0D
                    ? DataResult.success(new ItemAuraComponent(value))
                    : DataResult.error(() -> "Item aura remainder must be finite and non-negative"),
            ItemAuraComponent::remain
    );

    public ItemAuraComponent {
        if (!Double.isFinite(remain) || remain < 0.0D)
            throw new IllegalArgumentException("Item aura remainder must be finite and non-negative");
    }
}
