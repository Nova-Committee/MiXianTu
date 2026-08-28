package com.iafenvoy.mxt.data.quality;

import com.iafenvoy.mxt.data.badge.BadgeCodecs;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFixedCodec;

/**
 * Shared item quality definition, including common economic, forging, and
 * alchemy modifiers.
 *
 * <p>Ordering and groups deliberately belong to native registry tags rather
 * than this definition, so datapacks can reorganise them without rewriting
 * individual quality files.</p>
 */
public record ItemQuality(Component displayName, Modifier valueMultiplier,
                          Modifier forgingModifier, Modifier alchemyModifier,
                          EntityCondition condition) {
    public static final Codec<Holder<ItemQuality>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.ITEM_QUALITY);
    public static final Codec<ItemQuality> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            BadgeCodecs.TRANSLATABLE_COMPONENT.fieldOf("display_name").forGetter(ItemQuality::displayName),
            Modifier.CODEC.optionalFieldOf("value_multiplier", Modifier.DEFAULT).forGetter(ItemQuality::valueMultiplier),
            Modifier.CODEC.optionalFieldOf("forging_modifier", Modifier.DEFAULT).forGetter(ItemQuality::forgingModifier),
            Modifier.CODEC.optionalFieldOf("alchemy_modifier", Modifier.DEFAULT).forGetter(ItemQuality::alchemyModifier),
            EntityCondition.optionalCodec("condition").forGetter(ItemQuality::condition)
    ).apply(i, ItemQuality::new));

    public record Modifier(Component description, NumberProvider modifier) {
        public static final Modifier DEFAULT = new Modifier(Component.empty(), new Constant(1.0D));
        public static final Codec<Modifier> CODEC = RecordCodecBuilder.create(i -> i.group(
                BadgeCodecs.TRANSLATABLE_COMPONENT.fieldOf("description").forGetter(Modifier::description),
                NumberProvider.CODEC.optionalFieldOf("modifier", new Constant(1.0D)).forGetter(Modifier::modifier)
        ).apply(i, Modifier::new));
    }
}
