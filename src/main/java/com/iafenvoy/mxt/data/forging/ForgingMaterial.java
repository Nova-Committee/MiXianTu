package com.iafenvoy.mxt.data.forging;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;

/**
 * One entry of a blueprint's order-independent material requirement. Deliberately an id + count pair rather than
 * an {@link ItemStack}: native datapack registries are parsed before item components are bound, so
 * {@code ItemStack.CODEC} fails here, and the {@link Item} is resolved lazily instead.
 */
public record ForgingMaterial(Identifier id, int count) {
    public static final Codec<ForgingMaterial> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(ForgingMaterial::id),
            Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(ForgingMaterial::count)
    ).apply(i, ForgingMaterial::new));

    public Item item() {
        return BuiltInRegistries.ITEM.getOptional(this.id).orElse(Items.AIR);
    }

    public ItemStack createStack() {
        Item item = this.item();
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, this.count);
    }

    public boolean matches(ItemStack stack) {
        Item item = this.item();
        return item != Items.AIR && !stack.isEmpty() && stack.is(item);
    }

    public Optional<Item> resolve() {
        return BuiltInRegistries.ITEM.getOptional(this.id);
    }
}
