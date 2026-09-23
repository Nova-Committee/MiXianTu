package com.iafenvoy.mxt.data.quality;

import com.iafenvoy.mxt.registry.MxtResourceKeys;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;

/**
 * Tag conventions used by the item-quality registry. Ladders are entries of their own now, so the only tag left
 * here is the display-order one.
 */
public final class ItemQualityTags {
    public static final TagKey<ItemQuality> TOOLTIP_ORDER = create(Identifier.fromNamespaceAndPath("mxt", "tooltip_order"));

    private ItemQualityTags() {
    }

    private static TagKey<ItemQuality> create(Identifier id) {
        return TagKey.create(MxtResourceKeys.ITEM_QUALITY, id);
    }
}
