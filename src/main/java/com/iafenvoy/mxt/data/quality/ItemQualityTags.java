package com.iafenvoy.mxt.data.quality;

import com.iafenvoy.mxt.registry.MxtResourceKeys;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;

/**
 * Tag conventions used by the item-quality registry.
 */
public final class ItemQualityTags {
    private static final String GROUP_PREFIX = "group/";
    public static final TagKey<ItemQuality> TOOLTIP_ORDER = create(Identifier.fromNamespaceAndPath("mxt", "tooltip_order"));

    private ItemQualityTags() {
    }

    /**
     * Creates a group tag. {@code mxt_test:forged} maps to
     * {@code mxt_test:group/forged}; passing an already-prefixed path is also
     * accepted.
     */
    public static TagKey<ItemQuality> group(Identifier id) {
        Identifier tagId = id.withPath(path -> path.startsWith(GROUP_PREFIX) ? path : GROUP_PREFIX + path);
        return create(tagId);
    }

    public static boolean isGroup(TagKey<ItemQuality> tag) {
        return tag.registry().equals(MxtResourceKeys.ITEM_QUALITY) && tag.location().getPath().startsWith(GROUP_PREFIX);
    }

    private static TagKey<ItemQuality> create(Identifier id) {
        return TagKey.create(MxtResourceKeys.ITEM_QUALITY, id);
    }
}
