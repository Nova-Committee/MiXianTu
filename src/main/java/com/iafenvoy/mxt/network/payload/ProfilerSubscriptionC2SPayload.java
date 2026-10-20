package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record ProfilerSubscriptionC2SPayload(UUID token, boolean open) implements CustomPacketPayload {
    public static final Type<ProfilerSubscriptionC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "profiler_subscription"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProfilerSubscriptionC2SPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeUUID(value.token);
                buffer.writeBoolean(value.open);
            }, buffer -> new ProfilerSubscriptionC2SPayload(buffer.readUUID(), buffer.readBoolean()));

    @Override
    public @NonNull Type<ProfilerSubscriptionC2SPayload> type() {
        return TYPE;
    }
}
