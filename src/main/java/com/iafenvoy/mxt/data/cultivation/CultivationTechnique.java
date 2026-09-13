package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.condition.EntityCondition;
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
 * A learnable technique grants named abilities and cultivation modifiers.
 *
 * <p>An ability may be granted unconditionally ({@code granted_abilities}) or unlocked by mastery
 * ({@code stage_abilities}, keyed by {@link SkillStage}). The two are independent: the first list is
 * always active, the second becomes available as the holder's stage in the technique's chain
 * advances. {@code default_stage} is the chain entry point and may be omitted by a technique that
 * defines no mastery; a technique with stage-gated abilities or advancement conditions must declare
 * it, because the chain a mastery level belongs to is only reachable through that entry point.</p>
 *
 * <p>{@code advance_conditions} is keyed by the level a holder advances <em>to</em>: one chain is
 * shared by every technique that names it, while the conditions to climb it are the technique's own.
 * A key without an entry is an advancement with no extra requirement.</p>
 */
public record CultivationTechnique(String grade, EntityCondition learnCondition, List<Identifier> exclusiveTags,
                                   NumberProvider cultivationModifier, List<AttributeEntry> passiveModifiers,
                                   List<Either<Holder<Ability>, TagKey<Ability>>> grantedAbilities,
                                   Optional<Holder<SkillStage>> defaultStage,
                                   Map<Holder<SkillStage>, List<Either<Holder<Ability>, TagKey<Ability>>>> stageAbilities,
                                   Map<Holder<SkillStage>, EntityCondition> advanceConditions) {
    public static final Codec<Holder<CultivationTechnique>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.CULTIVATION_TECHNIQUE);
    public static final Codec<CultivationTechnique> DIRECT_CODEC = RecordCodecBuilder.<CultivationTechnique>create(i -> i.group(
            Codec.STRING.optionalFieldOf("grade", "common").forGetter(CultivationTechnique::grade),
            EntityCondition.optionalCodec("learn_condition").forGetter(CultivationTechnique::learnCondition),
            Identifier.CODEC.listOf().optionalFieldOf("exclusive_tags", List.of()).forGetter(CultivationTechnique::exclusiveTags),
            NumberProvider.CODEC.optionalFieldOf("cultivation_modifier", new Constant(1.0D)).forGetter(CultivationTechnique::cultivationModifier),
            AttributeEntry.CODEC.listOf().optionalFieldOf("passive_modifiers", List.of()).forGetter(CultivationTechnique::passiveModifiers),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).optionalFieldOf("granted_abilities", List.of()).forGetter(CultivationTechnique::grantedAbilities),
            SkillStage.CODEC.optionalFieldOf("default_stage").forGetter(CultivationTechnique::defaultStage),
            Codec.unboundedMap(SkillStage.CODEC, RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY))
                    .optionalFieldOf("stage_abilities", Map.of()).forGetter(CultivationTechnique::stageAbilities),
            Codec.unboundedMap(SkillStage.CODEC, EntityCondition.CODEC)
                    .optionalFieldOf("advance_conditions", Map.of()).forGetter(CultivationTechnique::advanceConditions)
    ).apply(i, CultivationTechnique::new)).validate(CultivationTechnique::validate);

    private static DataResult<CultivationTechnique> validate(CultivationTechnique technique) {
        if (technique.defaultStage().isEmpty() && !technique.stageAbilities().isEmpty())
            return DataResult.error(() -> "stage_abilities needs default_stage to name the skill chain it belongs to");
        if (technique.defaultStage().isEmpty() && !technique.advanceConditions().isEmpty())
            return DataResult.error(() -> "advance_conditions needs default_stage to name the skill chain it belongs to");
        return DataResult.success(technique);
    }
}
