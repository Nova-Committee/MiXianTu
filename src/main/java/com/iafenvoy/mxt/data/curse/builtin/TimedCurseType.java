package com.iafenvoy.mxt.data.curse.builtin;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.curse.CurseType;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.OptionalLong;

public enum TimedCurseType implements CurseType {
    INSTANCE;
    public static final MapCodec<TimedCurseType> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "timed");
    }

    @Override
    public MapCodec<TimedCurseType> codec() {
        return CODEC;
    }

    @Override
    public OptionalLong expiry(long duration, long gameTime) {
        return duration <= 0L ? OptionalLong.empty() : OptionalLong.of(Math.addExact(gameTime, duration));
    }
}
