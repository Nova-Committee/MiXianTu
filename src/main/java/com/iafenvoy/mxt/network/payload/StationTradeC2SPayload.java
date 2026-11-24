package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Requests the only customer-side action a station exposes. The open menu supplies all context.
 */
public record StationTradeC2SPayload(long revision) implements CustomPacketPayload {
    public static final Type<StationTradeC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "station_trade_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StationTradeC2SPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.LONG, StationTradeC2SPayload::revision, StationTradeC2SPayload::new);

    @Override
    public @NonNull Type<StationTradeC2SPayload> type() {
        return TYPE;
    }
}
