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
                         Optional<Holder<Tribulation>> tribulation, Optional<ParticleEffect> breakthroughParticle,
                         EntityAction successAction, EntityAction failAction) implements NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.REALM_STAGE.identifier());

    public RealmStage {
        if (breakthroughExp instanceof Constant(double value) && maxExperience instanceof Constant(
                double value1
        ) && value > value1)
            throw new IllegalArgumentException("Realm breakthrough minimum experience cannot exceed maximum experience");
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
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).optionalFieldOf("ability_requirements", List.of()).forGetter(RealmStage::abilityRequirements),
            // Eighteen components; two pairs keep the group at sixteen.
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
            autoBreakthrough, passiveModifiers, breakthroughCosts, abilityRequirements,
            tribulation.getFirst(), tribulation.getSecond(), actions.getFirst(), actions.getSecond())));

    // Next-realm links are holder references, so diagnostic output must remain shallow.
    @Override
    public @NonNull String toString() {
        return "RealmStage[aura=" + HolderHelper.id(this.aura) + ", hasNextRealm=" + this.nextRealm.isPresent()
                + ", breakthroughConditions=" + this.breakthrough.conditions().size() + ", costs=" + this.breakthroughCosts.size()
                + ", minorStages=" + this.minorStages.size()
                + ", abilityRequirements=" + this.abilityRequirements.size() + ", hasTribulation=" + this.tribulation.isPresent() + "]";
    }
}
