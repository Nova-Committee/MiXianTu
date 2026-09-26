package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.IconReference;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A learnable technique grants named abilities and cultivation modifiers, unconditionally
 * ({@code granted_abilities}) or by mastery ({@code configuration}), whose levels come from a shared
 * {@link SkillStage} chain. {@code default_stage} is the chain entry point and is mandatory as soon as any level
 * is configured; {@code mastery_resource} names the stored value that measures mastery. {@code quality} is the
 * technique's own tier: what the panel shows as its 品阶 and the tier its carrier item starts on.
 */
public record Technique(Component name, Component description, Optional<Holder<ItemQuality>> quality,
                        Optional<IconReference> icon,
                        EntityCondition learnCondition,
                        List<Identifier> exclusiveTags,
                        NumberProvider cultivationModifier, List<AttributeEntry> passiveModifiers,
                        List<Either<Holder<Ability>, TagKey<Ability>>> grantedAbilities,
                        Optional<Holder<SkillStage>> defaultStage,
                        Optional<Holder<Resource>> masteryResource,
                        Map<Holder<SkillStage>, StageConfiguration> configuration) implements NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.TECHNIQUE.identifier());
    public static final Codec<Holder<Technique>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.TECHNIQUE);
    public static final Codec<Technique> DIRECT_CODEC = RecordCodecBuilder.<Technique>create(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(Technique::name),
            ContextNameCodec.description(CATEGORY).forGetter(Technique::description),
            ItemQuality.CODEC.optionalFieldOf("quality").forGetter(Technique::quality),
            IconReference.CODEC.optionalFieldOf("icon").forGetter(Technique::icon),
            EntityCondition.optionalCodec("learn_condition").forGetter(Technique::learnCondition),
            Identifier.CODEC.listOf().optionalFieldOf("exclusive_tags", List.of()).forGetter(Technique::exclusiveTags),
            NumberProvider.CODEC.optionalFieldOf("cultivation_modifier", new Constant(1.0D)).forGetter(Technique::cultivationModifier),
            AttributeEntry.CODEC.listOf().optionalFieldOf("passive_modifiers", List.of()).forGetter(Technique::passiveModifiers),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).optionalFieldOf("granted_abilities", List.of()).forGetter(Technique::grantedAbilities),
            SkillStage.CODEC.optionalFieldOf("default_stage").forGetter(Technique::defaultStage),
            Resource.CODEC.optionalFieldOf("mastery_resource").forGetter(Technique::masteryResource),
            Codec.unboundedMap(SkillStage.CODEC, StageConfiguration.CODEC)
                    .optionalFieldOf("configuration", Map.of()).forGetter(Technique::configuration)
    ).apply(i, Technique::new)).validate(Technique::validate);

    private static DataResult<Technique> validate(Technique technique) {
        if (technique.defaultStage().isEmpty() && !technique.configuration().isEmpty())
            return DataResult.error(() -> "configuration needs default_stage to name the skill chain it belongs to");
        if (technique.defaultStage().isEmpty() && technique.masteryResource().isPresent())
            return DataResult.error(() -> "mastery_resource needs default_stage to name the skill chain it measures");
        return DataResult.success(technique);
    }

    // condition is required - a level that needs nothing writes mxt:always - and ability may be omitted by a
    // level that grants nothing.
    public record StageConfiguration(EntityCondition condition,
                                     List<Either<Holder<Ability>, TagKey<Ability>>> abilities) {
        public static final Codec<StageConfiguration> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityCondition.CODEC.fieldOf("condition").forGetter(StageConfiguration::condition),
                RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).optionalFieldOf("ability", List.of()).forGetter(StageConfiguration::abilities)
        ).apply(i, StageConfiguration::new));
    }
}
