package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.runtime.wheel.WheelLayout;
import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Server-authoritative wheel state: the twelve cells of the configured page plus the number of the cell the use
 * key would spend while nothing is on screen. Both fields are {@link Optional} - an absent layout means "never
 * configured" (an all-empty one means cleared), an absent number means nothing is armed.
 *
 * <p>The armed value is a <strong>number</strong> and nothing else. Cells are numbered across the whole wheel -
 * the configured page is {@code 0..11}, and the pages read from what the player carries follow from {@code 12}
 * on - so the number is enough to remember a place, and it deliberately does not name the entry: a number whose
 * entry is gone (the item was put away, the list got shorter) is still the number that finds it again when it
 * comes back. What an entry <em>is</em> is resolved on both sides at the moment it is used, from the page it
 * claims to come from.</p>
 *
 * <p>Derived pages therefore have no field here either: they are read from what the player carries, so there is
 * nothing about them to save.</p>
 */
public final class WheelLayoutAttachment extends ShouldSyncAttachment {
    public static final MapCodec<WheelLayoutAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            WheelLayout.CODEC.optionalFieldOf("layout").forGetter(WheelLayoutAttachment::layout),
            Codec.INT.optionalFieldOf("armed").forGetter(WheelLayoutAttachment::armed)
    ).apply(i, WheelLayoutAttachment::new));

    private Optional<WheelLayout> layout = Optional.empty();
    private Optional<Integer> armed = Optional.empty();

    public WheelLayoutAttachment() {
    }

    private WheelLayoutAttachment(Optional<WheelLayout> layout, Optional<Integer> armed) {
        this.layout = layout;
        this.armed = armed;
    }

    public Optional<WheelLayout> layout() {
        return this.layout;
    }

    public Optional<Integer> armed() {
        return this.armed;
    }

    public void setLayout(WheelLayout layout) {
        this.layout = Optional.ofNullable(layout);
        this.markDirty();
    }

    public void setArmed(Optional<Integer> armed) {
        this.armed = armed;
        this.markDirty();
    }
}
