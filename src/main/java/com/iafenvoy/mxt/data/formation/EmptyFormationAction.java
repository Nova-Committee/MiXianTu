package com.iafenvoy.mxt.data.formation;

import com.mojang.serialization.MapCodec;

/**
 * The registry's default module: declares nothing and does nothing.
 *
 * <p>Every formation action registry is a defaulted registry, so one entry has to hold the default id.
 * This is that entry, and it is also the honest way to write "this array has no function module of its
 * own" in a definition that only uses the generic action hooks.</p>
 */
public enum EmptyFormationAction implements FormationActionType {
    INSTANCE;
    public static final MapCodec<EmptyFormationAction> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<EmptyFormationAction> codec() {
        return CODEC;
    }
}
