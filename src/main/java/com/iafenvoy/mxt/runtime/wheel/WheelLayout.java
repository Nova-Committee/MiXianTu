package com.iafenvoy.mxt.runtime.wheel;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The twelve sectors of one player's wheel, index 0 being the one straight up; a short list is padded with
 * {@link WheelSlot#EMPTY} and an over-long one is cut, so the size is an invariant, not a convention.
 */
public record WheelLayout(List<WheelSlot> slots) {
    public static final int SLOTS = 12;
    public static final Codec<WheelLayout> CODEC = WheelSlot.LIST_CODEC.xmap(WheelLayout::new, WheelLayout::slots);
    public static final StreamCodec<RegistryFriendlyByteBuf, WheelLayout> STREAM_CODEC =
            WheelSlot.LIST_STREAM_CODEC.map(WheelLayout::new, WheelLayout::slots);
    public static final WheelLayout EMPTY = new WheelLayout(List.of());

    public WheelLayout {
        List<WheelSlot> normalized = new ArrayList<>(SLOTS);
        for (int index = 0; index < SLOTS; index++) {
            WheelSlot slot = index < slots.size() ? slots.get(index) : null;
            normalized.add(slot == null ? WheelSlot.EMPTY : slot);
        }
        slots = List.copyOf(normalized);
    }

    /** The content of one sector; out-of-range indices answer an empty slot rather than throwing. */
    public WheelSlot slot(int sector) {
        return sector < 0 || sector >= SLOTS ? WheelSlot.EMPTY : this.slots.get(sector);
    }

    public WheelLayout with(int sector, WheelSlot slot) {
        if (sector < 0 || sector >= SLOTS) return this;
        List<WheelSlot> replaced = new ArrayList<>(this.slots);
        replaced.set(sector, slot == null ? WheelSlot.EMPTY : slot);
        return new WheelLayout(replaced);
    }

    public boolean isEmpty() {
        return this.slots.stream().allMatch(WheelSlot::isEmpty);
    }

    @Override
    public @NonNull String toString() {
        return "WheelLayout[configured=" + (SLOTS - this.slots.stream().filter(WheelSlot::isEmpty).count()) + "/" + SLOTS + "]";
    }
}
