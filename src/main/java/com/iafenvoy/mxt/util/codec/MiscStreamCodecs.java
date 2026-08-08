package com.iafenvoy.mxt.util.codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public final class MiscStreamCodecs {
    public static <T extends Enum<T>> StreamCodec<ByteBuf, T> enumCodec(Class<T> clazz) {
        if (!clazz.isEnum()) throw new IllegalArgumentException("Class must be an enum");
        return ByteBufCodecs.idMapper(x -> clazz.getEnumConstants()[x], Enum::ordinal);
    }
}
