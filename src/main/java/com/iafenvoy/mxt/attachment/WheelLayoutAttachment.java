package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.runtime.wheel.WheelLayout;
import com.iafenvoy.mxt.runtime.wheel.WheelSlot;
import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Server-authoritative wheel state: the twelve sectors plus the armed entry. Both fields are
 * {@link Optional} - an absent layout means "never configured" (an all-empty one means cleared), an absent
 * selection means nothing is armed or the armed definition is gone.
 */
public final class WheelLayoutAttachment extends ShouldSyncAttachment {
    public static final MapCodec<WheelLayoutAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            WheelLayout.CODEC.optionalFieldOf("layout").forGetter(WheelLayoutAttachment::layout),
            WheelSlot.CODEC.optionalFieldOf("selection").forGetter(WheelLayoutAttachment::selection)
    ).apply(i, WheelLayoutAttachment::new));

    private Optional<WheelLayout> layout = Optional.empty();
    private Optional<WheelSlot> selection = Optional.empty();

    public WheelLayoutAttachment() {
    }

    private WheelLayoutAttachment(Optional<WheelLayout> layout, Optional<WheelSlot> selection) {
        this.layout = layout;
        this.selection = selection;
    }

    public Optional<WheelLayout> layout() {
        return this.layout;
    }

    public Optional<WheelSlot> selection() {
        return this.selection;
    }

    public void setLayout(WheelLayout layout) {
        this.layout = Optional.ofNullable(layout);
        this.markDirty();
    }

    public void setSelection(Optional<WheelSlot> selection) {
        this.selection = selection;
        this.markDirty();
    }
}
