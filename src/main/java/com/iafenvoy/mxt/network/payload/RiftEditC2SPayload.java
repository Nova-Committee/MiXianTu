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

public record RiftEditC2SPayload(UUID token, String action, long revision, String body) implements CustomPacketPayload {
    public static final Type<RiftEditC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "rift_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RiftEditC2SPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RiftEditC2SPayload::token,
            ByteBufCodecs.STRING_UTF8, RiftEditC2SPayload::action,
            ByteBufCodecs.LONG, RiftEditC2SPayload::revision,
            ByteBufCodecs.STRING_UTF8, RiftEditC2SPayload::body,
            RiftEditC2SPayload::new);

    @Override
    public @NonNull Type<RiftEditC2SPayload> type() {
        return TYPE;
    }
}
