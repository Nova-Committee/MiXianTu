package com.iafenvoy.mxt.data.badge.builtin;

import com.iafenvoy.mxt.data.badge.Badge;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

/**
 * Default no-op badge type.
 */
public enum EmptyBadge implements Badge {
    INSTANCE;
    public static final MapCodec<EmptyBadge> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public Identifier sprite() {
        return Identifier.withDefaultNamespace("missingno");
    }

    @Override
    public MapCodec<EmptyBadge> codec() {
        return CODEC;
    }
}
