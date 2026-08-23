package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Starts or stops the server-authoritative spirit-burst keybind.
 */
public record SpiritBurstC2SPayload(boolean firing, Optional<Identifier> resource) implements CustomPacketPayload {
    public static final Type<SpiritBurstC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "spirit_burst_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpiritBurstC2SPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SpiritBurstC2SPayload::firing,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), SpiritBurstC2SPayload::resource,
            SpiritBurstC2SPayload::new
    );

    @Override
    public @NonNull Type<SpiritBurstC2SPayload> type() {
        return TYPE;
    }
}
