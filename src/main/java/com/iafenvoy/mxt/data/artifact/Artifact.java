package com.iafenvoy.mxt.data.artifact;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.action.builtin.item.ConsumeHealthItemAction;
import com.iafenvoy.mxt.data.artifact.ability.ArtifactAbility;
import com.iafenvoy.mxt.data.artifact.ability.FlightArtifactAbility;
import com.iafenvoy.mxt.data.artifact.ability.GrantArtifactAbility;
import com.iafenvoy.mxt.data.artifact.ability.StorageArtifactAbility;
import com.iafenvoy.mxt.data.artifact.ability.ToggableArtifactAbility;
import com.iafenvoy.mxt.data.artifact.ability.UpkeepArtifactAbility;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.iafenvoy.mxt.util.matcher.ItemMatcher.Entry;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The rules of one artifact, shared by every item {@code items} opts into them. There is no field naming a
 * "kind": the definition's own registry id is that name, and a pack that wants one label over several
 * definitions says it with an item tag. Aura amounts live in the shared {@code mxt:spirit_storage} component and
 * only the per-aura ceiling is declared here. At most one {@code mxt:flight}, {@code mxt:storage} and
 * {@code mxt:upkeep} entry, and at most one togglable per key (the wheel addresses a cell by artifact id + key).
 */
public record Artifact(Component name, Component description, List<Entry> items,
                       Map<Holder<Aura>, NumberProvider> spiritCapacity,
                       List<ArtifactAbility> abilities, boolean curiosEquipable, boolean requireOwner,
                       ItemAction claimAction, EntityCondition claimCondition,
                       ItemAction pourAction, ItemAction useAction, NumberProvider holdTicks,
                       List<Either<Holder<Element>, TagKey<Element>>> element,
                       double attachmentMultiplier) implements ItemMatcher, NamedDefinition {
    // Two hearts.
    public static final double DEFAULT_CLAIM_HEALTH = 4.0D;
    // One second.
    public static final double DEFAULT_HOLD_TICKS = 20.0D;
    // The price of a claim has one shape everywhere: replacing this action is how a pack makes a claim free
    // (mxt:no_op) or charges something else.
    public static final ItemAction DEFAULT_CLAIM_ACTION = new ConsumeHealthItemAction(new Constant(DEFAULT_CLAIM_HEALTH));
    public static final Codec<Holder<Artifact>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.ARTIFACT);
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.ARTIFACT.identifier());
    private static final MapCodec<Artifact> RAW_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(Artifact::name),
            ContextNameCodec.description(CATEGORY).forGetter(Artifact::description),
            ENTRIES_CODEC.fieldOf("items").forGetter(Artifact::items),
            CollectionCodecs.map(Aura.CODEC, NumberProvider.CODEC).optionalFieldOf("spirit_capacity", Map.of()).forGetter(Artifact::spiritCapacity),
            ArtifactAbility.CODEC.listOf().optionalFieldOf("abilities", List.of()).forGetter(Artifact::abilities),
            Codec.BOOL.optionalFieldOf("curios_equipable", false).forGetter(Artifact::curiosEquipable),
            Codec.BOOL.optionalFieldOf("require_owner", false).forGetter(Artifact::requireOwner),
            ItemAction.CODEC.optionalFieldOf("claim_action", DEFAULT_CLAIM_ACTION).forGetter(Artifact::claimAction),
            EntityCondition.optionalCodec("claim_condition").forGetter(Artifact::claimCondition),
            ItemAction.optionalCodec("pour_action").forGetter(Artifact::pourAction),
            ItemAction.optionalCodec("use_action").forGetter(Artifact::useAction),
            NumberProvider.CODEC.optionalFieldOf("hold_ticks", new Constant(DEFAULT_HOLD_TICKS)).forGetter(Artifact::holdTicks),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).optionalFieldOf("element", List.of()).forGetter(Artifact::element),
            MiscCodecs.NON_NEGATIVE.optionalFieldOf("attachment_multiplier", 1.0D).forGetter(Artifact::attachmentMultiplier)
    ).apply(i, Artifact::new));
    // Unknown keys are dropped, so the keys this record used to carry are not read any more: a pack still writing
    // granted_abilities, flight_speed, refine_action, refine_condition, refine_health_cost, claim_cost or
    // item_type loads and those keys do nothing.
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
        // The wheel addresses a capability by this artifact's id and the capability's own key together, so one
        // key is one cell: two entries claiming the same key would be two cells a layout cannot tell apart.
        Set<String> keys = new HashSet<>();
        for (ArtifactAbility ability : abilities)
            if (ability instanceof ToggableArtifactAbility togglable && !keys.add(togglable.key()))
                throw new IllegalArgumentException("An artifact can declare one " + togglable.key() + " ability");
    }

    @Override
    public List<Entry> entries() {
        return this.items;
    }

    // Tags are expanded by the caller, which is the side that has a registry to expand them against.
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

    // One per key (see the constructor), so "this artifact plus a key" names exactly one of them.
    public List<ToggableArtifactAbility> toggables() {
        return this.abilities.stream().filter(ToggableArtifactAbility.class::isInstance)
                .map(ToggableArtifactAbility.class::cast).toList();
    }

    private <T extends ArtifactAbility> Optional<T> findAbility(Class<T> type) {
        return this.abilities.stream().filter(type::isInstance).map(type::cast).findFirst();
    }
}
