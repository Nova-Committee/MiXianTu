package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.ParticleEffect;
import com.iafenvoy.mxt.data.Tribulation;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.chain.ChainNode;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;

/**
 * One named realm stage, belonging to one {@link Aura}: the chain it is a link of, what it costs, and what it
 * takes to arrive here from the stage below. Conditions and outcomes are resolved by the cultivation runtime.
 */
public record RealmStage(Holder<Aura> aura, NumberProvider auraShareWeight,
                         EntityCondition cultivateCondition, Optional<Holder<RealmStage>> nextRealm,
                         NumberProvider breakthroughExp, NumberProvider maxExperience, int minorStages,
                         CultivateConditions breakthrough, boolean autoBreakthrough,
                         List<AttributeEntry> passiveModifiers, Optional<NumberProvider> lifespan,
                         List<Cost> breakthroughCosts,
                         List<Either<Holder<Ability>, TagKey<Ability>>> abilityRequirements,
                         List<MinorStageAbilities> minorStageAbilities, Optional<Holder<Tribulation>> tribulation,
                         Optional<ParticleEffect> breakthroughParticle, EntityAction successAction,
                         EntityAction failAction) implements ChainNode<RealmStage> {
    /**
     * The ceiling on {@code minor_stages}, which only decides how finely a realm's progress is divided.
     */
    public static final int MAX_MINOR_STAGES = 1024;
    public static final Codec<Holder<RealmStage>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.REALM_STAGE);
    public static final Codec<RealmStage> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Aura.CODEC.fieldOf("aura").forGetter(RealmStage::aura),
            NumberProvider.CODEC.optionalFieldOf("aura_share_weight", new Constant(1.0D)).forGetter(RealmStage::auraShareWeight),
            EntityCondition.optionalCodec("cultivate_condition").forGetter(RealmStage::cultivateCondition),
            CODEC.optionalFieldOf("next_realm").forGetter(RealmStage::nextRealm),
            NumberProvider.CODEC.optionalFieldOf("breakthrough_exp", new Constant(0.0D)).forGetter(RealmStage::breakthroughExp),
            NumberProvider.CODEC.optionalFieldOf("max_experience", new Constant(Double.MAX_VALUE)).forGetter(RealmStage::maxExperience),
            // A count, not a list: the names of the stages are derived from this realm's own id.
            Codec.INT.optionalFieldOf("minor_stages", 0).forGetter(RealmStage::minorStages),
            CultivateConditions.CODEC.optionalFieldOf("breakthrough", CultivateConditions.EMPTY).forGetter(RealmStage::breakthrough),
            Codec.BOOL.optionalFieldOf("auto_breakthrough", false).forGetter(RealmStage::autoBreakthrough),
            // Sixteen components; three pairs keep the group at sixteen.
            MiscCodecs.pair(
                            AttributeEntry.CODEC.listOf().optionalFieldOf("passive_modifiers", List.of()),
                            NumberProvider.CODEC.optionalFieldOf("lifespan"))
                    .forGetter(stage -> Pair.of(stage.passiveModifiers(), stage.lifespan())),
            Cost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(RealmStage::breakthroughCosts),
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
    ).apply(i, (aura, auraShareWeight, cultivateCondition, nextRealm, breakthroughExp,
                maxExperience, minorStages, breakthrough, autoBreakthrough, passive, breakthroughCosts,
                abilityRequirements, tribulation, actions) -> new RealmStage(aura,
            auraShareWeight, cultivateCondition, nextRealm, breakthroughExp, maxExperience, minorStages, breakthrough,
            autoBreakthrough, passive.getFirst(), passive.getSecond(), breakthroughCosts,
            abilityRequirements.getFirst(), abilityRequirements.getSecond(),
            tribulation.getFirst(), tribulation.getSecond(), actions.getFirst(), actions.getSecond())));

    /**
     * What standing on one minor stage of this realm unlocks: an entry is active from its own {@code stage}
     * onwards (0-based, the same index {@code minor_stage} reports), and the unlock stays once reached.
     */
    public record MinorStageAbilities(int stage, List<Either<Holder<Ability>, TagKey<Ability>>> abilities) {
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
        // Lifespan is only ever added, so a negative stage would silently shorten a life; a formula is judged at
        // the breakthrough instead.
        if (lifespan.isPresent() && lifespan.get() instanceof Constant(double value)
                && (!Double.isFinite(value) || value < 0.0D))
            throw new IllegalArgumentException("Realm stage lifespan must be finite and non-negative");
        if (minorStages < 0 || minorStages > MAX_MINOR_STAGES)
            throw new IllegalArgumentException("Realm minor_stages must be between 0 and " + MAX_MINOR_STAGES
                    + ": " + minorStages);
        // An unlock whose stage is not one of this realm's minor stages could never be reached, and two entries
        // on one stage would be the same threshold written twice.
        for (MinorStageAbilities entry : minorStageAbilities)
            if (entry.stage() < 0 || entry.stage() >= minorStages)
                throw new IllegalArgumentException("minor_stage_abilities names stage " + entry.stage()
                        + " but this realm declares " + minorStages + " minor stages");
        if (minorStageAbilities.stream().map(MinorStageAbilities::stage).distinct().count() != minorStageAbilities.size())
            throw new IllegalArgumentException("minor_stage_abilities must not name the same stage twice");
    }

    // Next-realm links are holder references, so diagnostic output must remain shallow.
    @Override
    public @NonNull String toString() {
        return "RealmStage[aura=" + HolderHelper.id(this.aura) + ", hasNextRealm=" + this.nextRealm.isPresent()
                + ", breakthroughConditions=" + this.breakthrough.conditions().size() + ", costs=" + this.breakthroughCosts.size()
                + ", minorStages=" + this.minorStages
                + ", abilityRequirements=" + this.abilityRequirements.size()
                + ", minorStageAbilities=" + this.minorStageAbilities.size()
                + ", hasTribulation=" + this.tribulation.isPresent() + "]";
    }

    @Override
    public Optional<Holder<RealmStage>> next() {
        return this.nextRealm;
    }
}
