package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.util.codec.MiscStreamCodecs;
import io.netty.buffer.ByteBuf;
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
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerTradeActionC2SPayload> STREAM_CODEC = StreamCodec.composite(
            PlayerTradeAction.STREAM_CODEC, PlayerTradeActionC2SPayload::action,
            PlayerTradeActionC2SPayload::new
    );

    @Override
    public @NonNull Type<PlayerTradeActionC2SPayload> type() {
        return TYPE;
    }

    /**
     * Multiple player-trade state changes share one enum-backed payload.
     */
    public enum PlayerTradeAction {
        ACCEPT,
        CANCEL_ACCEPT,
        CLOSE;
        public static final StreamCodec<ByteBuf, PlayerTradeAction> STREAM_CODEC = MiscStreamCodecs.enumCodec(PlayerTradeAction.class);
    }
}
