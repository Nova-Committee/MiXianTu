package com.iafenvoy.mxt.data.artifact;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.action.builtin.item.ConsumeHealthItemAction;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Map;

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
public record Artifact(List<Entry> items, Map<Holder<Aura>, NumberProvider> spiritCapacity,
                       List<Either<Holder<Ability>, TagKey<Ability>>> abilities, boolean curiosEquipable,
                       boolean requireOwner, ItemAction claimAction, EntityCondition claimCondition,
                       ItemAction pourAction, ItemAction useAction, NumberProvider holdTicks,
                       List<Either<Holder<Element>, TagKey<Element>>> element, double attachmentMultiplier,
                       int priority) implements ItemMatcher {
    public static final double DEFAULT_CLAIM_HEALTH = 4.0D, DEFAULT_HOLD_TICKS = 20.0D;
    public static final ItemAction DEFAULT_CLAIM_ACTION = new ConsumeHealthItemAction(new Constant(DEFAULT_CLAIM_HEALTH));
    public static final Codec<Holder<Artifact>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.ARTIFACT);
    public static final Codec<Artifact> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
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
            MiscCodecs.NON_NEGATIVE.optionalFieldOf("attachment_multiplier", 1.0D).forGetter(Artifact::attachmentMultiplier),
            Codec.INT.optionalFieldOf("priority", DEFAULT_PRIORITY).forGetter(Artifact::priority)
    ).apply(i, Artifact::new));

    public Artifact {
        if (items.isEmpty())
            throw new IllegalArgumentException("items must match at least one item");
    }

    @Override
    public List<Entry> entries() {
        return this.items;
    }
}
