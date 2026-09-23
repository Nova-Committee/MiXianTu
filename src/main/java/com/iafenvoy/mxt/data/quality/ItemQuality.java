package com.iafenvoy.mxt.data.quality;

import com.iafenvoy.mxt.api.NamedDefinition;
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

import java.util.Optional;

/**
 * Shared item quality definition, including common economic, forging and alchemy modifiers. Ordering and groups
 * belong to native registry tags rather than this definition, so datapacks can reorganise them without rewriting
 * individual quality files.
 *
 * <p>{@code name} and {@code description} may be omitted, in which case they are the entry's own translation
 * keys; each modifier's own {@code description} is shown only when the pack writes one.
 *
 * <p>{@code color} is optional and is the pack's own answer to "what colour is this tier": every place that names
 * a quality tints with it, and a quality without one is drawn exactly as it always was.
 */
public record ItemQuality(Component name, Component description, Optional<Integer> color, Modifier valueMultiplier,
                          Modifier forgingModifier, Modifier alchemyModifier,
                          EntityCondition condition) implements NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.ITEM_QUALITY.identifier());
    public static final Codec<Holder<ItemQuality>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.ITEM_QUALITY);
    public static final Codec<ItemQuality> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(ItemQuality::name),
            ContextNameCodec.description(CATEGORY).forGetter(ItemQuality::description),
            MiscCodecs.COLOR_NO_ALPHA.optionalFieldOf("color").forGetter(ItemQuality::color),
            Modifier.CODEC.optionalFieldOf("value_multiplier", Modifier.DEFAULT).forGetter(ItemQuality::valueMultiplier),
            Modifier.CODEC.optionalFieldOf("forging_modifier", Modifier.DEFAULT).forGetter(ItemQuality::forgingModifier),
            Modifier.CODEC.optionalFieldOf("alchemy_modifier", Modifier.DEFAULT).forGetter(ItemQuality::alchemyModifier),
            EntityCondition.optionalCodec("condition").forGetter(ItemQuality::condition)
    ).apply(i, ItemQuality::new));

    public record Modifier(Component description, NumberProvider modifier) {
        public static final Modifier DEFAULT = new Modifier(Component.empty(), new Constant(1.0D));
        public static final Codec<Modifier> CODEC = RecordCodecBuilder.create(i -> i.group(
                MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("description", Component.empty()).forGetter(Modifier::description),
                NumberProvider.CODEC.optionalFieldOf("modifier", new Constant(1.0D)).forGetter(Modifier::modifier)
        ).apply(i, Modifier::new));
    }
}
