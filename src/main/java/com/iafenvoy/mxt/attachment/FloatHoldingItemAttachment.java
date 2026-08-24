package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

/**
 * One temporarily reserved item stack shared by server-side item consumers.
 */
public final class FloatHoldingItemAttachment extends ShouldSyncAttachment {
    public static final MapCodec<FloatHoldingItemAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemStack.CODEC.optionalFieldOf("item", ItemStack.EMPTY).forGetter(FloatHoldingItemAttachment::item)
    ).apply(i, FloatHoldingItemAttachment::new));
    private ItemStack item;

    public FloatHoldingItemAttachment() {
        this(ItemStack.EMPTY);
    }

    private FloatHoldingItemAttachment(ItemStack item) {
        this.item = item;
    }

    public ItemStack item() {
        return this.item;
    }

    public void set(ItemStack item) {
        this.item = item;
        this.markDirty();
    }

    public ItemStack take() {
        ItemStack result = this.item;
        this.item = ItemStack.EMPTY;
        this.markDirty();
        return result;
    }

    public void clear() {
        this.item = ItemStack.EMPTY;
        this.markDirty();
    }
}
