package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.DescribedEntry;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;

import java.util.List;

/**
 * The ordered entity actions one item runs, attached to an already registered physical item. The item is the data
 * map's key. {@code element} is what the item is made of; an item whose definitions declare no element at all falls
 * back to the element of the aura it stores or declares. What the item is worth as a ward belongs to
 * {@code artifact} alone.
 */
public record ItemBinding(List<EntityAction> actions,
                          List<DescribedEntry<EntityCondition>> conditions,
                          List<Either<Holder<Element>, TagKey<Element>>> element,
                          int priority) {
    public static final Codec<ItemBinding> CODEC = RecordCodecBuilder.create(i -> i.group(
            EntityAction.SINGLE_CODEC.listOf().optionalFieldOf("actions", List.of()).forGetter(ItemBinding::actions),
            DescribedEntry.codec(EntityCondition.CODEC, "condition").listOf().optionalFieldOf("conditions", List.of()).forGetter(ItemBinding::conditions),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).optionalFieldOf("element", List.of()).forGetter(ItemBinding::element),
            Codec.INT.optionalFieldOf("priority", ItemMatcher.DEFAULT_PRIORITY).forGetter(ItemBinding::priority)
    ).apply(i, ItemBinding::new));
}
