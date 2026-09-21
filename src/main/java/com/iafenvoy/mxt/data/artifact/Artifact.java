package com.iafenvoy.mxt.data.artifact;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.action.builtin.item.ConsumeHealthItemAction;
import com.iafenvoy.mxt.data.artifact.ability.ArtifactAbility;
import com.iafenvoy.mxt.data.artifact.ability.FlightArtifactAbility;
import com.iafenvoy.mxt.data.artifact.ability.GrantArtifactAbility;
import com.iafenvoy.mxt.data.artifact.ability.StorageArtifactAbility;
import com.iafenvoy.mxt.data.artifact.ability.UpkeepArtifactAbility;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.iafenvoy.mxt.util.matcher.ItemMatcher.Entry;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The rules of one artifact, shared by every item {@code items} opts into them.
 *
 * <p>An artifact is not a new item but an existing one that behaves differently, so {@code items} answers "is
 * this stack an artifact, and which one" - the same reading {@code item_binding} and {@code spirit_herb} use.
 * There is no field naming a "kind" of artifact: the definition's own registry id is that name, and a pack that
 * wants one label over several definitions says it with an item tag. What it does is a list of
 * {@link ArtifactAbility} entries, one entry per kind (granting skills, flight,
 * storage, upkeep) rather than a group of fields on this record; flight, storage and upkeep are meaningful once
 * each, so a second one is refused below. Aura amounts live in the shared {@code mxt:spirit_storage} component
 * and only the per-aura ceiling is declared here; the feeding bonus that raises it stays in
 * {@link com.iafenvoy.mxt.runtime.artifact.ArtifactService}. Whether flight, storage and upkeep insist on an
 * owner is {@code require_owner} (or the entry's own {@code owner_only}): off, an artifact is open to anyone
 * until it is refined, and answers to its owner from then on.</p>
 *
 * <p>Claiming is described by three fields: what the gesture has to be ({@code hold_ticks}), whether it is
 * allowed at all ({@code claim_condition}) and what it does ({@code claim_action}). The gesture that reads them
 * is one implementation in {@link com.iafenvoy.mxt.runtime.artifact.ArtifactHoldService}.</p>
 *
 * <p>The price is one of the things {@code claim_action} does rather than a field of its own: its default is
 * {@link ConsumeHealthItemAction} for {@link #DEFAULT_CLAIM_HEALTH}
 * points of health, so an artifact that says nothing still charges two hearts to claim, and a definition that
 * wants a free binding says {@code mxt:no_op}. Every other action-shaped field defaults to doing nothing. What
 * this action charges is the whole price - three writers of a binding run it, and none of them asks whether the
 * holder can afford it.</p>
 */
public record Artifact(List<Entry> items, Map<Holder<Aura>, NumberProvider> spiritCapacity,
                       List<ArtifactAbility> abilities, boolean curiosEquipable, boolean requireOwner,
                       ItemAction claimAction, EntityCondition claimCondition,
                       ItemAction pourAction, ItemAction useAction, NumberProvider holdTicks) implements ItemMatcher {
    /** What a definition that does not say costs to claim: four points of health, two hearts. */
    public static final double DEFAULT_CLAIM_HEALTH = 4.0D;
    /** How long the gesture lasts when a definition does not say: one second. */
    public static final double DEFAULT_HOLD_TICKS = 20.0D;
    /**
     * What claiming does when a definition does not say, which is also what it costs: the price has one shape
     * everywhere, so a pack replaces it with any other action and a free claim is {@code mxt:no_op}.
     */
    public static final ItemAction DEFAULT_CLAIM_ACTION = new ConsumeHealthItemAction(new Constant(DEFAULT_CLAIM_HEALTH));
    public static final Codec<Holder<Artifact>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.ARTIFACT);
    private static final MapCodec<Artifact> RAW_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ENTRIES_CODEC.fieldOf("items").forGetter(Artifact::items),
            CollectionCodecs.map(Aura.CODEC, NumberProvider.CODEC).optionalFieldOf("spirit_capacity", Map.of()).forGetter(Artifact::spiritCapacity),
            ArtifactAbility.CODEC.listOf().optionalFieldOf("abilities", List.of()).forGetter(Artifact::abilities),
            Codec.BOOL.optionalFieldOf("curios_equipable", false).forGetter(Artifact::curiosEquipable),
            Codec.BOOL.optionalFieldOf("require_owner", false).forGetter(Artifact::requireOwner),
            ItemAction.CODEC.optionalFieldOf("claim_action", DEFAULT_CLAIM_ACTION).forGetter(Artifact::claimAction),
            EntityCondition.optionalCodec("claim_condition").forGetter(Artifact::claimCondition),
            ItemAction.optionalCodec("pour_action").forGetter(Artifact::pourAction),
            ItemAction.optionalCodec("use_action").forGetter(Artifact::useAction),
            NumberProvider.CODEC.optionalFieldOf("hold_ticks", new Constant(DEFAULT_HOLD_TICKS)).forGetter(Artifact::holdTicks)
    ).apply(i, Artifact::new));
    /**
     * Unknown keys are dropped, so the keys this record used to carry are not read any more: a pack still
     * writing {@code granted_abilities}, {@code flight_speed}, {@code refine_action}, {@code refine_condition},
     * {@code refine_health_cost}, {@code claim_cost} or {@code item_type} loads and those keys do nothing.
     */
    public static final Codec<Artifact> DIRECT_CODEC = RAW_CODEC.codec();

    public Artifact {
        if (items.isEmpty())
            throw new IllegalArgumentException("items must match at least one item");
        if (abilities.stream().filter(FlightArtifactAbility.class::isInstance).count() > 1L)
            throw new IllegalArgumentException("An artifact can declare at most one mxt:flight ability");
        if (abilities.stream().filter(StorageArtifactAbility.class::isInstance).count() > 1L)
            throw new IllegalArgumentException("An artifact can declare at most one mxt:storage ability");
        if (abilities.stream().filter(UpkeepArtifactAbility.class::isInstance).count() > 1L)
            throw new IllegalArgumentException("An artifact can declare at most one mxt:upkeep ability");
    }

    @Override
    public List<Entry> entries() {
        return this.items;
    }

    /**
     * The abilities this definition grants, in the order the entries write them. Tags are expanded by the
     * caller, which is the side that has a registry to expand them against.
     */
    public List<Either<Holder<Ability>, TagKey<Ability>>> grantedAbilities() {
        return this.abilities.stream()
                .filter(GrantArtifactAbility.class::isInstance)
                .map(GrantArtifactAbility.class::cast)
                .flatMap(grant -> grant.abilities().stream())
                .toList();
    }

    public Optional<FlightArtifactAbility> flight() {
        return this.findAbility(FlightArtifactAbility.class);
    }

    public Optional<StorageArtifactAbility> storage() {
        return this.findAbility(StorageArtifactAbility.class);
    }

    public Optional<UpkeepArtifactAbility> upkeep() {
        return this.findAbility(UpkeepArtifactAbility.class);
    }

    private <T extends ArtifactAbility> Optional<T> findAbility(Class<T> type) {
        return this.abilities.stream().filter(type::isInstance).map(type::cast).findFirst();
    }
}
