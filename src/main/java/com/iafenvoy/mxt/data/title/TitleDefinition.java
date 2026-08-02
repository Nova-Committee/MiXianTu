package com.iafenvoy.mxt.data.title;

import com.iafenvoy.mxt.data.common.AttributeModifierDefinition;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.runtime.cultivation.CultivationCondition;
import com.iafenvoy.mxt.util.codec.AutoIgnoreListCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * A displayable title with conflict groups and passive bonuses.
 */
public record TitleDefinition(List<CultivationCondition> unlockConditions,
                              String translationKey,
                              int priority, List<AttributeModifierDefinition> passiveModifiers,
                              List<Identifier> exclusiveTags, int maximumLevel) {
    public static final Codec<TitleDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            AutoIgnoreListCodec.create(MxtTypeRegistries.CULTIVATION_CONDITION.byNameCodec()).optionalFieldOf("unlock_conditions", List.of()).forGetter(TitleDefinition::unlockConditions), Codec.STRING.optionalFieldOf("translation_key", "").forGetter(TitleDefinition::translationKey),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(TitleDefinition::priority), AttributeModifierDefinition.CODEC.listOf().optionalFieldOf("passive_modifiers", List.of()).forGetter(TitleDefinition::passiveModifiers),
            Identifier.CODEC.listOf().optionalFieldOf("exclusive_tags", List.of()).forGetter(TitleDefinition::exclusiveTags), Codec.intRange(1, 1_000).optionalFieldOf("maximum_level", 1).forGetter(TitleDefinition::maximumLevel)
    ).apply(instance, TitleDefinition::new));
}
