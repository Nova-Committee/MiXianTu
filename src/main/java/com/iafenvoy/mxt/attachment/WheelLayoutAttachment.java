package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.runtime.wheel.WheelLayout;
import com.iafenvoy.mxt.util.AbstractChangeTrackable;
import com.iafenvoy.mxt.util.codec.OptionalCodecs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Server-authoritative wheel state: the twelve cells of the configured page plus the number of the cell the use key
 * would spend while nothing is on screen. Both fields are {@link Optional} - an absent layout means "never
 * configured" (an all-empty one means cleared), an absent number means nothing is armed. The armed value is a
 * <strong>number</strong> and nothing else, numbered across the whole wheel, so it deliberately does not name the
 * entry: a number whose entry is gone is still the number that finds it again when it comes back.
 */
public final class WheelLayoutAttachment extends AbstractChangeTrackable {
    public static final MapCodec<WheelLayoutAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            WheelLayout.CODEC.lenientOptionalFieldOf("layout").forGetter(WheelLayoutAttachment::layout),
            OptionalCodecs.lenientIntCodec("armed").forGetter(WheelLayoutAttachment::armed)
    ).apply(i, WheelLayoutAttachment::new));
    private Optional<WheelLayout> layout = Optional.empty();
    private OptionalInt armed = OptionalInt.empty();

    public WheelLayoutAttachment() {
    }

    private WheelLayoutAttachment(Optional<WheelLayout> layout, OptionalInt armed) {
        this.layout = layout;
        this.armed = armed;
    }

    public Optional<WheelLayout> layout() {
        return this.layout;
    }

    public OptionalInt armed() {
        return this.armed;
    }

    public void setLayout(WheelLayout layout) {
        this.layout = Optional.ofNullable(layout);
        this.markDirty();
    }

    public void setArmed(OptionalInt armed) {
        this.armed = armed;
        this.markDirty();
    }
}
