package com.iafenvoy.mxt.screen.profiling;

import com.iafenvoy.saturn.screen.opener.OpenScreenData;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record ProfilerOpenScreenData(UUID token, boolean local) implements OpenScreenData {
    public static final StreamCodec<RegistryFriendlyByteBuf, ProfilerOpenScreenData> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ProfilerOpenScreenData::token,
            ByteBufCodecs.BOOL, ProfilerOpenScreenData::local,
            ProfilerOpenScreenData::new);

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, ? extends OpenScreenData> streamCodec() {
        return STREAM_CODEC;
    }
}
