package com.iafenvoy.mxt.data.aura;

import com.mojang.serialization.Codec;

/** Persistent spirit power stored by an item stack that implements {@code SpiritItemAccess}. */
public record SpiritStorageComponent(int amount) {
    public static final Codec<SpiritStorageComponent> CODEC = Codec.intRange(0, Integer.MAX_VALUE)
            .xmap(SpiritStorageComponent::new, SpiritStorageComponent::amount);
}
