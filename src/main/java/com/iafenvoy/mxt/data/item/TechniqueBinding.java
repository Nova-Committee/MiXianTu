package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.meta.AlwaysTrueEntityCondition;
import com.iafenvoy.mxt.data.cultivation.CultivationTechnique;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Optional;

/**
 * Binds an existing physical item to a learnable cultivation technique.
 */
public record TechniqueBinding(List<Entry> entries, Holder<CultivationTechnique> technique,
                               Optional<TagKey<ItemQuality>> qualityGroup, EntityCondition condition,
                               boolean setActive) implements ItemMatcher {
    public static final Codec<TechniqueBinding> CODEC = RecordCodecBuilder.create(i -> i.group(
            ENTRIES_CODEC.fieldOf("items").forGetter(TechniqueBinding::entries),
            CultivationTechnique.CODEC.fieldOf("technique").forGetter(TechniqueBinding::technique),
            TagKey.hashedCodec(MxtResourceKeys.ITEM_QUALITY).optionalFieldOf("quality_group").forGetter(TechniqueBinding::qualityGroup),
            EntityCondition.CODEC.optionalFieldOf("condition", AlwaysTrueEntityCondition.INSTANCE).forGetter(TechniqueBinding::condition),
            Codec.BOOL.optionalFieldOf("set_active", true).forGetter(TechniqueBinding::setActive)
    ).apply(i, TechniqueBinding::new));
}
