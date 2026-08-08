package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.data.AttributeModifier;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.meta.AlwaysTrueEntityCondition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;

import java.util.List;

/**
 * A learnable technique grants named abilities and cultivation modifiers.
 */
public record CultivationTechnique(String grade, EntityCondition learnCondition, List<Identifier> exclusiveTags,
                                   NumberProvider cultivationModifier, List<AttributeModifier> passiveModifiers,
                                   List<Either<Holder<Ability>, TagKey<Ability>>> grantedAbilities) {
    public static final Codec<Holder<CultivationTechnique>> CODEC = RegistryFixedCodec.create(MxtRegistryKeys.CULTIVATION_TECHNIQUE);
    public static final Codec<CultivationTechnique> DIRECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("grade", "common").forGetter(CultivationTechnique::grade),
            EntityCondition.CODEC.optionalFieldOf("learn_condition", AlwaysTrueEntityCondition.INSTANCE).forGetter(CultivationTechnique::learnCondition),
            Identifier.CODEC.listOf().optionalFieldOf("exclusive_tags", List.of()).forGetter(CultivationTechnique::exclusiveTags),
            NumberProvider.CODEC.optionalFieldOf("cultivation_modifier", new Constant(1.0D)).forGetter(CultivationTechnique::cultivationModifier),
            AttributeModifier.CODEC.listOf().optionalFieldOf("passive_modifiers", List.of()).forGetter(CultivationTechnique::passiveModifiers),
            RegistryCodecs.holderOrTagList(MxtRegistryKeys.ABILITY).optionalFieldOf("granted_abilities", List.of()).forGetter(CultivationTechnique::grantedAbilities)
    ).apply(instance, CultivationTechnique::new));
}
