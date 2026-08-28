package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Optional;

/**
 * Attaches ordered entity actions to already registered physical items.
 */
public record ItemBinding(List<Entry> entries, List<EntityAction> actions, Optional<TagKey<ItemQuality>> qualityGroup,
                          EntityCondition condition) implements ItemMatcher {
    public static final Codec<ItemBinding> CODEC = RecordCodecBuilder.create(i -> i.group(
            ENTRIES_CODEC.fieldOf("items").forGetter(ItemBinding::entries),
            EntityAction.SINGLE_CODEC.listOf().optionalFieldOf("actions", List.of()).forGetter(ItemBinding::actions),
            TagKey.hashedCodec(MxtResourceKeys.ITEM_QUALITY).optionalFieldOf("quality_group").forGetter(ItemBinding::qualityGroup),
            EntityCondition.optionalCodec("condition").forGetter(ItemBinding::condition)
    ).apply(i, ItemBinding::new));
}
