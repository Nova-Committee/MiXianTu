package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.runtime.rift.RiftVisual;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.UUID;

public record RiftSyncS2CPayload(Identifier dimension, UUID generation, UUID id, long revision, boolean reset,
                                 Optional<RiftVisual> visual) implements CustomPacketPayload {
    public static final Type<RiftSyncS2CPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "rift_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RiftSyncS2CPayload> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, RiftSyncS2CPayload::dimension,
            UUIDUtil.STREAM_CODEC, RiftSyncS2CPayload::generation,
            UUIDUtil.STREAM_CODEC, RiftSyncS2CPayload::id,
            ByteBufCodecs.LONG, RiftSyncS2CPayload::revision,
            ByteBufCodecs.BOOL, RiftSyncS2CPayload::reset,
            ByteBufCodecs.optional(RiftVisual.STREAM_CODEC), RiftSyncS2CPayload::visual,
            RiftSyncS2CPayload::new);

    @Override
    public @NonNull Type<RiftSyncS2CPayload> type() {
        return TYPE;
    }
}
