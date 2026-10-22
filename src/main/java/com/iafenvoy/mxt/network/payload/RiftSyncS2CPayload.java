package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.runtime.rift.RiftVisual;
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
    public static final Type<RiftSyncS2CPayload> TYPE = new Type<>(Identifier.parse("mxt:rift_sync"));
    private static final StreamCodec<RegistryFriendlyByteBuf, RiftVisual> VISUAL = ByteBufCodecs.fromCodecWithRegistries(RiftVisual.CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf, RiftSyncS2CPayload> STREAM_CODEC = StreamCodec.of((buf, value) -> {
        buf.writeIdentifier(value.dimension); buf.writeUUID(value.generation); buf.writeUUID(value.id);
        buf.writeVarLong(value.revision); buf.writeBoolean(value.reset); buf.writeBoolean(value.visual.isPresent());
        value.visual.ifPresent(visual -> VISUAL.encode(buf, visual));
    }, buf -> new RiftSyncS2CPayload(buf.readIdentifier(), buf.readUUID(), buf.readUUID(), buf.readVarLong(), buf.readBoolean(),
            buf.readBoolean() ? Optional.of(VISUAL.decode(buf)) : Optional.empty()));

    @Override
    public @NonNull Type<RiftSyncS2CPayload> type() { return TYPE; }
}
