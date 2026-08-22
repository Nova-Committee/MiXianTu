package com.iafenvoy.mxt.attachment;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

/**
 * One temporarily reserved item stack shared by server-side item consumers.
 */
public final class FloatHoldingItemComponent {
    public static final MapCodec<FloatHoldingItemComponent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemStack.CODEC.optionalFieldOf("item", ItemStack.EMPTY).forGetter(FloatHoldingItemComponent::item)
    ).apply(i, FloatHoldingItemComponent::new));
    private ItemStack item;

    public FloatHoldingItemComponent() {
        this(ItemStack.EMPTY);
    }

    private FloatHoldingItemComponent(ItemStack item) {
        this.item = item;
    }

    public ItemStack item() {
        return this.item;
    }

    public void set(ItemStack item) {
        this.item = item;
    }

    public ItemStack take() {
        ItemStack result = this.item;
        this.item = ItemStack.EMPTY;
        return result;
    }

    public void clear() {
        this.item = ItemStack.EMPTY;
    }
}
