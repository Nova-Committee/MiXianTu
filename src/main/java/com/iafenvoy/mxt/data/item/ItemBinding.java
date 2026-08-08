package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.ItemMatcher;
import com.iafenvoy.mxt.util.ItemMatcher.Entry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Attaches ordered entity actions to already registered physical items.
 */
public record ItemBinding(List<Entry> entries, List<EntityAction> actions) implements ItemMatcher {
    public static final Codec<ItemBinding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ENTRIES_CODEC.fieldOf("items").forGetter(ItemBinding::entries),
            EntityAction.SINGLE_CODEC.listOf().optionalFieldOf("actions", List.of()).forGetter(ItemBinding::actions)
    ).apply(instance, ItemBinding::new));
}
