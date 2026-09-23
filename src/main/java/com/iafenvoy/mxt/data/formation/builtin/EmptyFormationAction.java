package com.iafenvoy.mxt.data.formation.builtin;

import com.iafenvoy.mxt.data.formation.FormationActionType;
import com.mojang.serialization.MapCodec;

/**
 * The registry's default module: declares nothing and does nothing. Every formation action registry is a defaulted
 * registry, so one entry has to hold the default id, and this is also how a definition that uses only the generic
 * action hooks says it has no function module.
 */
public enum EmptyFormationAction implements FormationActionType {
    INSTANCE;
    public static final MapCodec<EmptyFormationAction> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<EmptyFormationAction> codec() {
        return CODEC;
    }
}
