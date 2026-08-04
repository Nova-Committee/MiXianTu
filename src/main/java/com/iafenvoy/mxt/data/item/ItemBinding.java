package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.ItemMatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Attaches ordered entity actions to already registered physical items.
 */
public record ItemBinding(List<ItemMatcher.Entry> entries, List<EntityAction> actions) implements ItemMatcher {
    public static final Codec<ItemBinding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemMatcher.ENTRIES_CODEC.fieldOf("items").forGetter(ItemBinding::entries),
            EntityAction.SINGLE_CODEC.listOf().optionalFieldOf("actions", List.of()).forGetter(ItemBinding::actions)
    ).apply(instance, ItemBinding::new));

    public ItemBinding {
        entries = List.copyOf(entries);
        actions = List.copyOf(actions);
    }
}
