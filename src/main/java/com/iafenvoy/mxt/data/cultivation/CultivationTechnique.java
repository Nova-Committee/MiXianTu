package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.data.AttributeModifier;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.iafenvoy.mxt.data.ability.Ability;
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
public record CultivationTechnique(String grade, List<CultivationCondition> learnConditions,
                                   List<Identifier> exclusiveTags, NumberProvider cultivationModifier,
                                   List<AttributeModifier> passiveModifiers,
                                   List<Either<Holder<Ability>, TagKey<Ability>>> grantedAbilities) {
    public static final Codec<CultivationTechnique> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("grade", "common").forGetter(CultivationTechnique::grade),
            AutoIgnoreListCodec.create(MxtTypeRegistries.CULTIVATION_CONDITION.byNameCodec()).optionalFieldOf("learn_conditions", List.of()).forGetter(CultivationTechnique::learnConditions),
            Identifier.CODEC.listOf().optionalFieldOf("exclusive_tags", List.of()).forGetter(CultivationTechnique::exclusiveTags),
            NumberProvider.CODEC.optionalFieldOf("cultivation_modifier", new Constant(1.0D)).forGetter(CultivationTechnique::cultivationModifier),
            AttributeModifier.CODEC.listOf().optionalFieldOf("passive_modifiers", List.of()).forGetter(CultivationTechnique::passiveModifiers),
            RegistryCodecs.holderOrTagList(MxtRegistryKeys.ABILITY).optionalFieldOf("granted_abilities", List.of()).forGetter(CultivationTechnique::grantedAbilities)
    ).apply(instance, CultivationTechnique::new));
}
