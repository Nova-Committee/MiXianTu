package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.data.common.AttributeModifierDefinition;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.iafenvoy.mxt.data.ability.AbilityDefinition;
import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.runtime.cultivation.CultivationCondition;
import com.iafenvoy.mxt.util.codec.AutoIgnoreListCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;

import java.util.List;

/**
 * A learnable technique grants named abilities and cultivation modifiers.
 */
public record CultivationTechniqueDefinition(String grade,
                                             List<CultivationCondition> learnConditions,
                                             List<Identifier> exclusiveTags, NumberProvider cultivationModifier,
                                             List<AttributeModifierDefinition> passiveModifiers,
                                             List<Either<Holder<AbilityDefinition>, TagKey<AbilityDefinition>>> grantedAbilities) {
    public static final Codec<CultivationTechniqueDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("grade", "common").forGetter(CultivationTechniqueDefinition::grade), AutoIgnoreListCodec.create(MxtTypeRegistries.CULTIVATION_CONDITION.byNameCodec()).optionalFieldOf("learn_conditions", List.of()).forGetter(CultivationTechniqueDefinition::learnConditions),
            Identifier.CODEC.listOf().optionalFieldOf("exclusive_tags", List.of()).forGetter(CultivationTechniqueDefinition::exclusiveTags), NumberProvider.CODEC.optionalFieldOf("cultivation_modifier", new Constant(1.0D)).forGetter(CultivationTechniqueDefinition::cultivationModifier),
            AttributeModifierDefinition.CODEC.listOf().optionalFieldOf("passive_modifiers", List.of()).forGetter(CultivationTechniqueDefinition::passiveModifiers), RegistryCodecs.holderOrTagList(MxtRegistryKeys.ABILITY).optionalFieldOf("granted_abilities", List.of()).forGetter(CultivationTechniqueDefinition::grantedAbilities)
    ).apply(instance, CultivationTechniqueDefinition::new));
}
