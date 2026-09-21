package com.iafenvoy.mxt.data.artifact;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.artifact.ability.ArtifactAbility;
import com.iafenvoy.mxt.data.artifact.ability.FlightArtifactAbility;
import com.iafenvoy.mxt.data.artifact.ability.GrantArtifactAbility;
import com.iafenvoy.mxt.data.artifact.ability.StorageArtifactAbility;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
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
 * What it does is a list of {@link ArtifactAbility} entries, one entry per kind (granting skills, flight,
 * storage) rather than a group of fields on this record; flight and storage are meaningful once each, so a
 * second one is refused below. Aura amounts live in the shared {@code mxt:spirit_storage} component and only
 * the per-aura ceiling is declared here; the feeding bonus that raises it stays in
 * {@link com.iafenvoy.mxt.runtime.artifact.ArtifactService}. Whether flight and the storage insist on an owner
 * is {@code require_owner}: off, an artifact is open to anyone until it is refined, and answers to its owner
 * from then on.</p>
 */
public record Artifact(String itemType, List<Entry> items, Map<Holder<Aura>, NumberProvider> spiritCapacity,
                       List<ArtifactAbility> abilities, boolean curiosEquipable, boolean requireOwner,
                       ItemAction refineAction) implements ItemMatcher {
    public static final Codec<Holder<Artifact>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.ARTIFACT);
    private static final MapCodec<Artifact> RAW_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("item_type").forGetter(Artifact::itemType),
            ENTRIES_CODEC.fieldOf("items").forGetter(Artifact::items),
            CollectionCodecs.map(Aura.CODEC, NumberProvider.CODEC).optionalFieldOf("spirit_capacity", Map.of()).forGetter(Artifact::spiritCapacity),
            ArtifactAbility.CODEC.listOf().optionalFieldOf("abilities", List.of()).forGetter(Artifact::abilities),
            Codec.BOOL.optionalFieldOf("curios_equipable", false).forGetter(Artifact::curiosEquipable),
            Codec.BOOL.optionalFieldOf("require_owner", false).forGetter(Artifact::requireOwner),
            ItemAction.optionalCodec("refine_action").forGetter(Artifact::refineAction)
    ).apply(i, Artifact::new));
    /**
     * Unknown keys are dropped, so the keys this record used to carry are not read any more: a pack still
     * writing {@code granted_abilities} or {@code flight_speed} loads and those keys do nothing.
     */
    public static final Codec<Artifact> DIRECT_CODEC = RAW_CODEC.codec();

    public Artifact {
        if (itemType == null || itemType.isBlank())
            throw new IllegalArgumentException("item_type must not be blank");
        if (items.isEmpty())
            throw new IllegalArgumentException("items must match at least one item");
        if (abilities.stream().filter(FlightArtifactAbility.class::isInstance).count() > 1L)
            throw new IllegalArgumentException("An artifact can declare at most one mxt:flight ability");
        if (abilities.stream().filter(StorageArtifactAbility.class::isInstance).count() > 1L)
            throw new IllegalArgumentException("An artifact can declare at most one mxt:storage ability");
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

    private <T extends ArtifactAbility> Optional<T> findAbility(Class<T> type) {
        return this.abilities.stream().filter(type::isInstance).map(type::cast).findFirst();
    }
}
