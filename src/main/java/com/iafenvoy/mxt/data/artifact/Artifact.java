package com.iafenvoy.mxt.data.artifact;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.action.builtin.item.ConsumeHealthItemAction;
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
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;

import java.util.*;

/**
 * The rules of one artifact, shared by every item {@code items} opts into them. There is no field naming a
 * "kind": the definition's own registry id is that name, and a pack that wants one label over several
 * definitions says it with an item tag. Aura amounts live in the shared {@code mxt:spirit_storage} component and
 * only the per-aura ceiling is declared here.
 *
 * <p>{@code abilities} names what carrying this thing does: one {@code mxt:ability} id per entry, or a tag of
 * them. An artifact never holds an ability of its own, so every one it grants can also be granted by a book, a
 * command or a script, and the id it is granted under is the registry id in all four cases.
 */
public record Artifact(Component name, Component description, List<Entry> items,
                       Map<Holder<Aura>, NumberProvider> spiritCapacity,
                       List<Either<Holder<Ability>, TagKey<Ability>>> abilities, boolean curiosEquipable,
                       boolean requireOwner, ItemAction claimAction, EntityCondition claimCondition,
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
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).optionalFieldOf("abilities", List.of()).forGetter(Artifact::abilities),
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
    // item_type, or an ability written inside this file instead of named by id, loads and names nothing.
    public static final Codec<Artifact> DIRECT_CODEC = RAW_CODEC.codec();

    public Artifact {
        if (items.isEmpty())
            throw new IllegalArgumentException("items must match at least one item");
    }

    @Override
    public List<Entry> entries() {
        return this.items;
    }
}
