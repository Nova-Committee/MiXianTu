package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.DescribedEntry;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Optional;

/**
 * Attaches ordered entity actions to already registered physical items.
 *
 * <p>{@code element} is what this item is made of, which is the reading
 * {@link com.iafenvoy.mxt.runtime.cultivation.ItemElements} takes: an item whose definitions declare no element
 * at all falls back to the element of the aura it stores or declares.</p>
 *
 * <p>{@code attachment_multiplier} is what this item is worth as a ward: while it is carried, every strike that
 * leaves an element on the carrier leaves this fraction of it. Several carried items multiply and the default
 * {@code 1.0} is a no-op.</p>
 */
public record ItemBinding(List<Entry> entries, List<EntityAction> actions, Optional<TagKey<ItemQuality>> qualityGroup,
                          List<DescribedEntry<EntityCondition>> conditions,
                          List<Either<Holder<Element>, TagKey<Element>>> element, double attachmentMultiplier) implements ItemMatcher {
    public static final Codec<ItemBinding> CODEC = RecordCodecBuilder.create(i -> i.group(
            ENTRIES_CODEC.fieldOf("items").forGetter(ItemBinding::entries),
            EntityAction.SINGLE_CODEC.listOf().optionalFieldOf("actions", List.of()).forGetter(ItemBinding::actions),
            TagKey.hashedCodec(MxtResourceKeys.ITEM_QUALITY).optionalFieldOf("quality_group").forGetter(ItemBinding::qualityGroup),
            DescribedEntry.codec(EntityCondition.CODEC, "condition").listOf().optionalFieldOf("conditions", List.of()).forGetter(ItemBinding::conditions),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).optionalFieldOf("element", List.of()).forGetter(ItemBinding::element),
            MiscCodecs.NON_NEGATIVE.optionalFieldOf("attachment_multiplier", 1.0D).forGetter(ItemBinding::attachmentMultiplier)
    ).apply(i, ItemBinding::new));
}
