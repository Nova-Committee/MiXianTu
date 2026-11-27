package com.iafenvoy.mxt.data.aura;

import com.iafenvoy.saturn.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;

/**
 * Remaining fuel per item.
 */
public record ItemAuraComponent(double remain) {
    public static final Codec<ItemAuraComponent> CODEC = MiscCodecs.NON_NEGATIVE_DOUBLE.fieldOf("per_item")
            .codec().xmap(ItemAuraComponent::new, ItemAuraComponent::remain);
}
