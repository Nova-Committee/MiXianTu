package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.IconReference;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A learnable technique grants named abilities and cultivation modifiers, unconditionally
 * ({@code granted_abilities}) or by mastery ({@code configuration}), whose levels come from a shared
 * {@link SkillStage} chain. {@code default_stage} is the chain entry point and is mandatory as soon as any
 * level is configured; {@code mastery_resource} names the stored value that measures mastery.
 */
public record CultivationTechnique(String grade, Optional<IconReference> icon, EntityCondition learnCondition,
                                   List<Identifier> exclusiveTags,
                                   NumberProvider cultivationModifier, List<AttributeEntry> passiveModifiers,
                                   List<Either<Holder<Ability>, TagKey<Ability>>> grantedAbilities,
                                   Optional<Holder<SkillStage>> defaultStage,
                                   Optional<Holder<Resource>> masteryResource,
                                   Map<Holder<SkillStage>, StageConfiguration> configuration) {
    public static final Codec<Holder<CultivationTechnique>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.CULTIVATION_TECHNIQUE);
    public static final Codec<CultivationTechnique> DIRECT_CODEC = RecordCodecBuilder.<CultivationTechnique>create(i -> i.group(
            Codec.STRING.optionalFieldOf("grade", "common").forGetter(CultivationTechnique::grade),
            IconReference.CODEC.optionalFieldOf("icon").forGetter(CultivationTechnique::icon),
            EntityCondition.optionalCodec("learn_condition").forGetter(CultivationTechnique::learnCondition),
            Identifier.CODEC.listOf().optionalFieldOf("exclusive_tags", List.of()).forGetter(CultivationTechnique::exclusiveTags),
            NumberProvider.CODEC.optionalFieldOf("cultivation_modifier", new Constant(1.0D)).forGetter(CultivationTechnique::cultivationModifier),
            AttributeEntry.CODEC.listOf().optionalFieldOf("passive_modifiers", List.of()).forGetter(CultivationTechnique::passiveModifiers),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).optionalFieldOf("granted_abilities", List.of()).forGetter(CultivationTechnique::grantedAbilities),
            SkillStage.CODEC.optionalFieldOf("default_stage").forGetter(CultivationTechnique::defaultStage),
            Resource.CODEC.optionalFieldOf("mastery_resource").forGetter(CultivationTechnique::masteryResource),
            Codec.unboundedMap(SkillStage.CODEC, StageConfiguration.CODEC)
                    .optionalFieldOf("configuration", Map.of()).forGetter(CultivationTechnique::configuration)
    ).apply(i, CultivationTechnique::new)).validate(CultivationTechnique::validate);

    private static DataResult<CultivationTechnique> validate(CultivationTechnique technique) {
        if (technique.defaultStage().isEmpty() && !technique.configuration().isEmpty())
            return DataResult.error(() -> "configuration needs default_stage to name the skill chain it belongs to");
        if (technique.defaultStage().isEmpty() && technique.masteryResource().isPresent())
            return DataResult.error(() -> "mastery_resource needs default_stage to name the skill chain it measures");
        return DataResult.success(technique);
    }

    /**
     * What one level of the technique's chain means to this technique: the requirement to reach it
     * and the abilities it grants. {@code condition} is required - a level that needs nothing writes
     * {@code mxt:always_true} - and {@code ability} may be omitted by a level that grants nothing.
     */
    public record StageConfiguration(EntityCondition condition,
                                     List<Either<Holder<Ability>, TagKey<Ability>>> abilities) {
        public static final Codec<StageConfiguration> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityCondition.CODEC.fieldOf("condition").forGetter(StageConfiguration::condition),
                RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).optionalFieldOf("ability", List.of()).forGetter(StageConfiguration::abilities)
        ).apply(i, StageConfiguration::new));
    }
}
