package com.iafenvoy.mxt.registry.tag;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;

public final class MxtQualityTags {
    public static final TagKey<ItemQuality> TOOLTIP_ORDER = create("tooltip_order");

    private static TagKey<ItemQuality> create(String id) {
        return TagKey.create(MxtResourceKeys.ITEM_QUALITY, Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, id));
    }
}
