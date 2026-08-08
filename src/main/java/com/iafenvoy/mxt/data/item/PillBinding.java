package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.builtin.entity.meta.NoOpAction;
import com.iafenvoy.mxt.util.ItemMatcher.Entry;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.iafenvoy.mxt.util.ItemMatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Pill behaviour attached directly to an already registered consumable item.
 */
public record PillBinding(List<Entry> entries, EntityAction onConsume, NumberProvider toxicityGain,
                          NumberProvider toxicityThreshold, EntityAction onOverdose,
                          NumberProvider toxicityAfterOverdose) implements ItemMatcher {
    public static final Codec<PillBinding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ENTRIES_CODEC.fieldOf("items").forGetter(PillBinding::entries),
            EntityAction.CODEC.optionalFieldOf("on_consume", NoOpAction.INSTANCE).forGetter(PillBinding::onConsume),
            NumberProvider.CODEC.optionalFieldOf("toxicity_gain", new Constant(0.0D)).forGetter(PillBinding::toxicityGain),
            NumberProvider.CODEC.optionalFieldOf("toxicity_threshold", new Constant(Double.MAX_VALUE)).forGetter(PillBinding::toxicityThreshold),
            EntityAction.CODEC.optionalFieldOf("on_overdose", NoOpAction.INSTANCE).forGetter(PillBinding::onOverdose),
            NumberProvider.CODEC.optionalFieldOf("toxicity_after_overdose", new Constant(0.0D)).forGetter(PillBinding::toxicityAfterOverdose)
    ).apply(instance, PillBinding::new));
}
