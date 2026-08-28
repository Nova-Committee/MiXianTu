package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.iafenvoy.mxt.data.condition.EntityCondition;
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
                                   NumberProvider cultivationModifier, List<AttributeEntry> passiveModifiers,
                                   List<Either<Holder<Ability>, TagKey<Ability>>> grantedAbilities) {
    public static final Codec<Holder<CultivationTechnique>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.CULTIVATION_TECHNIQUE);
    public static final Codec<CultivationTechnique> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("grade", "common").forGetter(CultivationTechnique::grade),
            EntityCondition.optionalCodec("learn_condition").forGetter(CultivationTechnique::learnCondition),
            Identifier.CODEC.listOf().optionalFieldOf("exclusive_tags", List.of()).forGetter(CultivationTechnique::exclusiveTags),
            NumberProvider.CODEC.optionalFieldOf("cultivation_modifier", new Constant(1.0D)).forGetter(CultivationTechnique::cultivationModifier),
            AttributeEntry.CODEC.listOf().optionalFieldOf("passive_modifiers", List.of()).forGetter(CultivationTechnique::passiveModifiers),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).optionalFieldOf("granted_abilities", List.of()).forGetter(CultivationTechnique::grantedAbilities)
    ).apply(i, CultivationTechnique::new));
}
