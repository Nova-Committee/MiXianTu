package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.data.DescribedEntry;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.NoOpAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.List;

/**
 * What one pill does: the dose action, the toxicity it adds and the price of an overdose, plus the colour the built-in
 * carrier paints itself with. Which items are this pill and how often they may be used belong to a binding instead.
 */
public record Pill(Component name, Component description, int color, EntityAction onConsume,
                   NumberProvider toxicityGain, NumberProvider toxicityThreshold, EntityAction onOverdose,
                   NumberProvider toxicityAfterOverdose, List<DescribedEntry<EntityCondition>> conditions)
        implements NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.PILL.identifier());
    // White is the carrier texture as authored: a pack that writes no colour gets the plain grey pill.
    public static final int DEFAULT_COLOR = 0xFFFFFF;
    public static final Codec<Holder<Pill>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.PILL);
    public static final Codec<Pill> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(Pill::name),
            ContextNameCodec.description(CATEGORY).forGetter(Pill::description),
            MiscCodecs.RGB_COLOR.optionalFieldOf("color", DEFAULT_COLOR).forGetter(Pill::color),
            EntityAction.optionalCodec("on_consume").forGetter(Pill::onConsume),
            NumberProvider.CODEC.optionalFieldOf("toxicity_gain", new Constant(0.0D)).forGetter(Pill::toxicityGain),
            NumberProvider.CODEC.optionalFieldOf("toxicity_threshold", new Constant(Double.MAX_VALUE)).forGetter(Pill::toxicityThreshold),
            EntityAction.optionalCodec("on_overdose").forGetter(Pill::onOverdose),
            NumberProvider.CODEC.optionalFieldOf("toxicity_after_overdose", new Constant(0.0D)).forGetter(Pill::toxicityAfterOverdose),
            DescribedEntry.codec(EntityCondition.CODEC, "condition").listOf().optionalFieldOf("conditions", List.of()).forGetter(Pill::conditions)
    ).apply(i, Pill::new));

    /**
     * What a stack carrying only effect overrides reads as: no definition claimed it, so every field is the one the
     * codec would have supplied and nothing here names a holder.
     */
    public static Pill defaults() {
        return new Pill(Component.translatable("item.mxt.pill"), Component.empty(), DEFAULT_COLOR, NoOpAction.INSTANCE,
                new Constant(0.0D), new Constant(Double.MAX_VALUE), NoOpAction.INSTANCE, new Constant(0.0D), List.of());
    }
}
