package com.iafenvoy.mxt.data.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;


/**
 * The one-way association between a physical item and a data definition.
 * The referenced definition never contains, nor discovers, its bound items.
 */
public record ItemBinding(Item item, DatapackItemReference definition) {
    public static final Codec<ItemBinding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(ItemBinding::item),
            DatapackItemReference.CODEC.fieldOf("definition").forGetter(ItemBinding::definition)
    ).apply(instance, ItemBinding::new));
}
