package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Optional;

/**
 * Pill behaviour attached directly to an already registered consumable item.
 */
public record PillBinding(List<Entry> entries, EntityAction onConsume, NumberProvider toxicityGain,
                          NumberProvider toxicityThreshold, EntityAction onOverdose,
                          NumberProvider toxicityAfterOverdose, Optional<TagKey<ItemQuality>> qualityGroup,
                          EntityCondition condition) implements ItemMatcher {
    public static final Codec<PillBinding> CODEC = RecordCodecBuilder.create(i -> i.group(
            ENTRIES_CODEC.fieldOf("items").forGetter(PillBinding::entries),
            EntityAction.optionalCodec("on_consume").forGetter(PillBinding::onConsume),
            NumberProvider.CODEC.optionalFieldOf("toxicity_gain", new Constant(0.0D)).forGetter(PillBinding::toxicityGain),
            NumberProvider.CODEC.optionalFieldOf("toxicity_threshold", new Constant(Double.MAX_VALUE)).forGetter(PillBinding::toxicityThreshold),
            EntityAction.optionalCodec("on_overdose").forGetter(PillBinding::onOverdose),
            NumberProvider.CODEC.optionalFieldOf("toxicity_after_overdose", new Constant(0.0D)).forGetter(PillBinding::toxicityAfterOverdose),
            TagKey.hashedCodec(MxtResourceKeys.ITEM_QUALITY).optionalFieldOf("quality_group").forGetter(PillBinding::qualityGroup),
            EntityCondition.optionalCodec("condition").forGetter(PillBinding::condition)
    ).apply(i, PillBinding::new));
}
