package com.iafenvoy.mxt.data.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * An ItemStack-shaped datapack value that creates its stack only after item
 * components have finished binding during server startup.
 */
public record ItemStackDefinition(Item item, int count, DataComponentPatch components) {
    public static final Codec<ItemStackDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("id").forGetter(ItemStackDefinition::item),
            ExtraCodecs.intRange(1, 99).optionalFieldOf("count", 1).forGetter(ItemStackDefinition::count),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(ItemStackDefinition::components)
    ).apply(instance, ItemStackDefinition::new));

    public ItemStack createStack() {
        ItemStack stack = new ItemStack(this.item, this.count);
        stack.applyComponents(this.components);
        return stack;
    }
}
