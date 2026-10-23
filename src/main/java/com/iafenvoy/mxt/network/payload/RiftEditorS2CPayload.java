package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.runtime.rift.RiftRecord;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.UUID;

public record RiftEditorS2CPayload(UUID token, Identifier dimension, String action, Optional<RiftRecord> record,
                                   String body) implements CustomPacketPayload {
    public static final Type<RiftEditorS2CPayload> TYPE = new Type<>(Identifier.parse("mxt:rift_editor"));
    private static final StreamCodec<RegistryFriendlyByteBuf, RiftRecord> RECORD = ByteBufCodecs.fromCodecWithRegistries(RiftRecord.CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf, RiftEditorS2CPayload> STREAM_CODEC = StreamCodec.of((buf, value) -> {
        buf.writeUUID(value.token);
        buf.writeIdentifier(value.dimension);
        buf.writeUtf(value.action, 24);
        buf.writeBoolean(value.record.isPresent());
        value.record.ifPresent(record -> RECORD.encode(buf, record));
        buf.writeUtf(value.body, 262144);
    }, buf -> new RiftEditorS2CPayload(buf.readUUID(), buf.readIdentifier(), buf.readUtf(24),
            buf.readBoolean() ? Optional.of(RECORD.decode(buf)) : Optional.empty(), buf.readUtf(262144)));

    @Override
    public @NonNull Type<RiftEditorS2CPayload> type() {
        return TYPE;
    }
}
