package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record ProfilerGuiS2CPayload(UUID token, boolean open, boolean local) implements CustomPacketPayload {
    public static final Type<ProfilerGuiS2CPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "profiler_gui"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProfilerGuiS2CPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeUUID(value.token);
                buffer.writeBoolean(value.open);
                buffer.writeBoolean(value.local);
            }, buffer -> new ProfilerGuiS2CPayload(buffer.readUUID(), buffer.readBoolean(), buffer.readBoolean()));

    @Override
    public @NonNull Type<ProfilerGuiS2CPayload> type() {
        return TYPE;
    }
}
