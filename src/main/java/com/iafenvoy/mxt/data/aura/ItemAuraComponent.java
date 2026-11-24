package com.iafenvoy.mxt.data.aura;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * Persistent fuel per item. The numeric legacy form describes the whole stack.
 */
public record ItemAuraComponent(double remain, boolean perItem) {
    private static final Codec<Double> AMOUNT = Codec.DOUBLE.validate(value -> Double.isFinite(value) && value >= 0.0D
            ? DataResult.success(value) : DataResult.error(() -> "Item aura remainder must be finite and non-negative"));
    private static final Codec<ItemAuraComponent> PER_ITEM = AMOUNT.fieldOf("per_item").codec()
            .xmap(ItemAuraComponent::new, ItemAuraComponent::remain);
    private static final Codec<ItemAuraComponent> LEGACY = AMOUNT.xmap(value -> new ItemAuraComponent(value, false), ItemAuraComponent::remain);
    public static final Codec<ItemAuraComponent> CODEC = Codec.either(PER_ITEM, LEGACY)
            .xmap(value -> value.map(left -> left, right -> right), value -> value.perItem
                    ? Either.left(value) : Either.right(value));

    public ItemAuraComponent(double remain) {
        this(remain, true);
    }

    public ItemAuraComponent normalize(int count) {
        return this.perItem ? this : new ItemAuraComponent(this.remain / Math.max(1, count));
    }

    public ItemAuraComponent {
        if (!Double.isFinite(remain) || remain < 0.0D)
            throw new IllegalArgumentException("Item aura remainder must be finite and non-negative");
    }
}
