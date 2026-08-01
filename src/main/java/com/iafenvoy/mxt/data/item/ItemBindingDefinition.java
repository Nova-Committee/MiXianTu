package com.iafenvoy.mxt.data.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;


/**
 * The one-way association between a physical item and a data definition.
 * The referenced definition never contains, nor discovers, its bound items.
 */
public record ItemBindingDefinition(Item item, ItemDefinitionReference definition) {
    public static final Codec<ItemBindingDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(ItemBindingDefinition::item),
            ItemDefinitionReference.CODEC.fieldOf("definition").forGetter(ItemBindingDefinition::definition)
    ).apply(instance, ItemBindingDefinition::new));
}
