package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/** Requests the only customer-side action a station exposes. The open menu supplies all context. */
public record StationTradeC2SPayload() implements CustomPacketPayload {
    public static final Type<StationTradeC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "station_trade_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StationTradeC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public StationTradeC2SPayload decode(RegistryFriendlyByteBuf buffer) {
            return new StationTradeC2SPayload();
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, StationTradeC2SPayload value) {
        }
    };

    @Override
    public @NonNull Type<StationTradeC2SPayload> type() {
        return TYPE;
    }
}
