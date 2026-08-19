package com.iafenvoy.mxt.data.quality;

import com.iafenvoy.mxt.data.badge.BadgeCodecs;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.meta.AlwaysTrueEntityCondition;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.Optional;

/**
 * Shared item quality definition, including common economic, forging, and
 * alchemy modifiers.
 *
 * <p>Ordering and groups deliberately belong to native registry tags rather
 * than this definition, so datapacks can reorganise them without rewriting
 * individual quality files.</p>
 */
public record ItemQuality(Component displayName, Optional<Component> description, NumberProvider valueMultiplier,
                          NumberProvider forgingModifier, NumberProvider alchemyModifier, EntityCondition condition) {
    public static final Codec<Holder<ItemQuality>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.ITEM_QUALITY);
    public static final Codec<ItemQuality> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            BadgeCodecs.TRANSLATABLE_COMPONENT.fieldOf("display_name").forGetter(ItemQuality::displayName),
            BadgeCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("description").forGetter(ItemQuality::description),
            NumberProvider.CODEC.optionalFieldOf("value_multiplier", new Constant(1.0D)).forGetter(ItemQuality::valueMultiplier),
            NumberProvider.CODEC.optionalFieldOf("forging_modifier", new Constant(1.0D)).forGetter(ItemQuality::forgingModifier),
            NumberProvider.CODEC.optionalFieldOf("alchemy_modifier", new Constant(1.0D)).forGetter(ItemQuality::alchemyModifier),
            EntityCondition.CODEC.optionalFieldOf("condition", AlwaysTrueEntityCondition.INSTANCE).forGetter(ItemQuality::condition)
    ).apply(i, ItemQuality::new));
}
