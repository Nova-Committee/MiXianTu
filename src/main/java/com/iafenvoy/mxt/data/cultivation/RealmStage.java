package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.ParticleEffect;
import com.iafenvoy.mxt.data.Tribulation;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.iafenvoy.mxt.util.codec.ContextNameListCodec;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;

/**
 * One named realm stage, belonging to one {@link Aura}: the chain it is a link of, what it costs, and what
 * happens when it gives way to the next. Conditions and outcomes are resolved by the cultivation runtime.
 */
public record RealmStage(Component name, Component description, Holder<Aura> aura,
                         NumberProvider auraShareWeight, EntityCondition cultivateCondition,
                         Optional<Holder<RealmStage>> nextRealm,
                         NumberProvider breakthroughExp, NumberProvider maxExperience, List<Component> minorStages,
                         CultivateConditions breakthrough,
                         boolean autoBreakthrough,
                         List<AttributeEntry> passiveModifiers, List<Cost> breakthroughCosts,
                         List<Either<Holder<Ability>, TagKey<Ability>>> abilityRequirements,
                         List<MinorStageAbilities> minorStageAbilities,
                         Optional<Holder<Tribulation>> tribulation, Optional<ParticleEffect> breakthroughParticle,
                         EntityAction successAction, EntityAction failAction) implements NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.REALM_STAGE.identifier());

    /**
     * What standing on one minor stage of this realm unlocks: an entry is active from its own {@code stage}
     * onwards (0-based, the same index {@code minor_stage} reports), and the unlock stays once reached.
     */
    public record MinorStageAbilities(int stage,
                                      List<Either<Holder<Ability>, TagKey<Ability>>> abilities) {
        public static final Codec<MinorStageAbilities> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("stage").forGetter(MinorStageAbilities::stage),
                RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).optionalFieldOf("ability", List.of()).forGetter(MinorStageAbilities::abilities)
        ).apply(i, MinorStageAbilities::new));
    }

    public RealmStage {
        if (breakthroughExp instanceof Constant(double value) && maxExperience instanceof Constant(
                double value1
        ) && value > value1)
            throw new IllegalArgumentException("Realm breakthrough minimum experience cannot exceed maximum experience");
        // An unlock whose stage is not one of this realm's minor stages could never be reached, and two entries
        // on one stage would be the same threshold written twice.
        int count = minorStages.size();
        for (MinorStageAbilities entry : minorStageAbilities)
            if (entry.stage() < 0 || entry.stage() >= count)
                throw new IllegalArgumentException("minor_stage_abilities names stage " + entry.stage()
                        + " but this realm declares " + count + " minor stages");
        if (minorStageAbilities.stream().map(MinorStageAbilities::stage).distinct().count() != minorStageAbilities.size())
            throw new IllegalArgumentException("minor_stage_abilities must not name the same stage twice");
    }

    public static final Codec<Holder<RealmStage>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.REALM_STAGE);
    public static final Codec<RealmStage> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(RealmStage::name),
            ContextNameCodec.description(CATEGORY).forGetter(RealmStage::description),
            Aura.CODEC.fieldOf("aura").forGetter(RealmStage::aura),
            NumberProvider.CODEC.optionalFieldOf("aura_share_weight", new Constant(1.0D)).forGetter(RealmStage::auraShareWeight),
            EntityCondition.optionalCodec("cultivate_condition").forGetter(RealmStage::cultivateCondition),
            RegistryFixedCodec.create(MxtResourceKeys.REALM_STAGE).optionalFieldOf("next_realm").forGetter(RealmStage::nextRealm),
            NumberProvider.CODEC.optionalFieldOf("breakthrough_exp", new Constant(0.0D)).forGetter(RealmStage::breakthroughExp),
            NumberProvider.CODEC.optionalFieldOf("max_experience", new Constant(Double.MAX_VALUE)).forGetter(RealmStage::maxExperience),
            ContextNameListCodec.minorStages(CATEGORY).forGetter(RealmStage::minorStages),
            CultivateConditions.CODEC.optionalFieldOf("breakthrough", CultivateConditions.EMPTY).forGetter(RealmStage::breakthrough),
            Codec.BOOL.optionalFieldOf("auto_breakthrough", false).forGetter(RealmStage::autoBreakthrough),
            AttributeEntry.CODEC.listOf().optionalFieldOf("passive_modifiers", List.of()).forGetter(RealmStage::passiveModifiers),
            Cost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(RealmStage::breakthroughCosts),
            // Eighteen components; three pairs keep the group at sixteen.
            MiscCodecs.pair(
                            RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).optionalFieldOf("ability_requirements", List.of()),
                            MinorStageAbilities.CODEC.listOf().optionalFieldOf("minor_stage_abilities", List.of()))
                    .forGetter(stage -> Pair.of(stage.abilityRequirements(), stage.minorStageAbilities())),
            MiscCodecs.pair(
                            Tribulation.CODEC.optionalFieldOf("tribulation"),
                            ParticleEffect.CODEC.optionalFieldOf("breakthrough_particle"))
                    .forGetter(stage -> Pair.of(stage.tribulation(), stage.breakthroughParticle())),
            MiscCodecs.pair(
                            EntityAction.optionalCodec("success_action"),
                            EntityAction.optionalCodec("fail_action"))
                    .forGetter(stage -> Pair.of(stage.successAction(), stage.failAction()))
    ).apply(i, (name, description, aura, auraShareWeight, cultivateCondition, nextRealm, breakthroughExp,
                maxExperience, minorStages, breakthrough, autoBreakthrough, passiveModifiers, breakthroughCosts,
                abilityRequirements, tribulation, actions) -> new RealmStage(name, description, aura,
            auraShareWeight, cultivateCondition, nextRealm, breakthroughExp, maxExperience, minorStages, breakthrough,
            autoBreakthrough, passiveModifiers, breakthroughCosts,
            abilityRequirements.getFirst(), abilityRequirements.getSecond(),
            tribulation.getFirst(), tribulation.getSecond(), actions.getFirst(), actions.getSecond())));

    // Next-realm links are holder references, so diagnostic output must remain shallow.
    @Override
    public @NonNull String toString() {
        return "RealmStage[aura=" + HolderHelper.id(this.aura) + ", hasNextRealm=" + this.nextRealm.isPresent()
                + ", breakthroughConditions=" + this.breakthrough.conditions().size() + ", costs=" + this.breakthroughCosts.size()
                + ", minorStages=" + this.minorStages.size()
                + ", abilityRequirements=" + this.abilityRequirements.size()
                + ", minorStageAbilities=" + this.minorStageAbilities.size()
                + ", hasTribulation=" + this.tribulation.isPresent() + "]";
    }
}
