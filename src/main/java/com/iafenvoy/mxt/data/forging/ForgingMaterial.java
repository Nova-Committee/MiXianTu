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
 * One entry of a blueprint's order-independent material requirement.
 *
 * <p>This is deliberately a plain {@code id} + {@code count} pair rather than an
 * {@link ItemStack}. Native datapack registries are parsed before item components are bound, so
 * {@code ItemStack.CODEC} cannot be used here; it fails with
 * "Item ... does not have components yet". Resolving the {@link Item} lazily inside
 * {@link #createStack()} keeps the codec loadable while still letting runtime matching compare
 * real stacks.</p>
 */
public record ForgingMaterial(Identifier id, int count) {
    public static final Codec<ForgingMaterial> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("id").forGetter(ForgingMaterial::id),
            Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(ForgingMaterial::count)
    ).apply(i, ForgingMaterial::new));

    /**
     * The declared item, or {@link Items#AIR} when the id is unknown.
     */
    public Item item() {
        return BuiltInRegistries.ITEM.getOptional(this.id).orElse(Items.AIR);
    }

    /**
     * A stack used for container matching. Empty when the item id does not resolve.
     */
    public ItemStack createStack() {
        Item item = this.item();
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, this.count);
    }

    /**
     * Whether this entry matches the given stack, ignoring the count.
     */
    public boolean matches(ItemStack stack) {
        Item item = this.item();
        return item != Items.AIR && !stack.isEmpty() && stack.is(item);
    }

    public Optional<Item> resolve() {
        return BuiltInRegistries.ITEM.getOptional(this.id);
    }
}
