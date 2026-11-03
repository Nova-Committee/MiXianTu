package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record ProfilerSubscriptionC2SPayload(UUID token, boolean open) implements CustomPacketPayload {
    public static final Type<ProfilerSubscriptionC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "profiler_subscription"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProfilerSubscriptionC2SPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ProfilerSubscriptionC2SPayload::token,
            ByteBufCodecs.BOOL, ProfilerSubscriptionC2SPayload::open,
            ProfilerSubscriptionC2SPayload::new);

    @Override
    public @NonNull Type<ProfilerSubscriptionC2SPayload> type() {
        return TYPE;
    }
}
