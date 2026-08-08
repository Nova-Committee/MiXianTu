package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.meta.AlwaysTrueEntityCondition;
import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.List;

/**
 * A displayable title with conflict groups and passive bonuses.
 */
public record Title(EntityCondition unlockCondition, String translationKey, int priority,
                    List<AttributeModifier> passiveModifiers, List<Identifier> exclusiveTags, int maximumLevel) {
    public static final Codec<Holder<Title>> CODEC = RegistryFixedCodec.create(MxtRegistryKeys.TITLE);
    public static final Codec<Title> DIRECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            EntityCondition.CODEC.optionalFieldOf("unlock_condition", AlwaysTrueEntityCondition.INSTANCE).forGetter(Title::unlockCondition),
            Codec.STRING.optionalFieldOf("translation_key", "").forGetter(Title::translationKey),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(Title::priority),
            AttributeModifier.CODEC.listOf().optionalFieldOf("passive_modifiers", List.of()).forGetter(Title::passiveModifiers),
            Identifier.CODEC.listOf().optionalFieldOf("exclusive_tags", List.of()).forGetter(Title::exclusiveTags),
            Codec.intRange(1, 1_000).optionalFieldOf("maximum_level", 1).forGetter(Title::maximumLevel)
    ).apply(instance, Title::new));
}
