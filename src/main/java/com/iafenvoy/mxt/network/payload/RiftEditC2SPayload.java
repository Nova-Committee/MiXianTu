package com.iafenvoy.mxt.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record RiftEditC2SPayload(UUID token, String action, long revision, String body) implements CustomPacketPayload {
    public static final Type<RiftEditC2SPayload> TYPE = new Type<>(Identifier.parse("mxt:rift_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RiftEditC2SPayload> STREAM_CODEC = StreamCodec.of((buf, value) -> {
        buf.writeUUID(value.token);
        buf.writeUtf(value.action, 24);
        buf.writeVarLong(value.revision);
        buf.writeUtf(value.body, 32767);
    }, buf -> new RiftEditC2SPayload(buf.readUUID(), buf.readUtf(24), buf.readVarLong(), buf.readUtf(32767)));

    @Override
    public @NonNull Type<RiftEditC2SPayload> type() {
        return TYPE;
    }
}
