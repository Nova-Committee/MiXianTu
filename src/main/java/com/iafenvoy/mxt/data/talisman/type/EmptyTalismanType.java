package com.iafenvoy.mxt.data.talisman.type;

import com.iafenvoy.mxt.data.talisman.TalismanType;
import com.iafenvoy.mxt.data.talisman.TalismanUse;
import com.mojang.serialization.MapCodec;

import java.util.List;

/**
 * The default type: an inscription that does nothing at all. Registered so that a {@code type} nobody wrote resolves
 * to a no-op instead of silently becoming one of the four real uses.
 */
public record EmptyTalismanType() implements TalismanType {
    public static final EmptyTalismanType INSTANCE = new EmptyTalismanType();
    public static final MapCodec<EmptyTalismanType> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public Plan plan(TalismanUse use) {
        return Plan.of(List.of());
    }

    @Override
    public void apply(TalismanUse use, Plan plan) {
    }

    @Override
    public MapCodec<EmptyTalismanType> codec() {
        return CODEC;
    }
}
