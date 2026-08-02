package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Changes the requesting player's state in an already-open player trade.
 */
public record PlayerTradeActionC2SPayload(PlayerTradeAction action) implements CustomPacketPayload {
    public static final Type<PlayerTradeActionC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "player_trade_action_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerTradeActionC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PlayerTradeActionC2SPayload decode(RegistryFriendlyByteBuf buffer) {
            return new PlayerTradeActionC2SPayload(buffer.readEnum(PlayerTradeAction.class));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, PlayerTradeActionC2SPayload value) {
            buffer.writeEnum(value.action());
        }
    };

    @Override
    public @NonNull Type<PlayerTradeActionC2SPayload> type() {
        return TYPE;
    }
}
