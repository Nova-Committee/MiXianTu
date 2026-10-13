package com.iafenvoy.mxt.data.curse.builtin;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.curse.CurseType;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;

import java.util.OptionalLong;

public enum PermanentCurseType implements CurseType {
    INSTANCE;
    public static final MapCodec<PermanentCurseType> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public Identifier id() {
        return Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "permanent");
    }

    @Override
    public MapCodec<PermanentCurseType> codec() {
        return CODEC;
    }

    @Override
    public OptionalLong expiry(long duration, long gameTime) {
        return OptionalLong.of(-1L);
    }
}
