package com.iafenvoy.mxt.runtime.economy;

import com.iafenvoy.mxt.util.codec.MiscStreamCodecs;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * What one side of a trade asks for: a domain action, so the menu and the packet are adapters over it rather than
 * the place it is defined.
 */
public enum PlayerTradeAction {
    ACCEPT,
    CANCEL_ACCEPT,
    CLOSE;

    public static final StreamCodec<ByteBuf, PlayerTradeAction> STREAM_CODEC = MiscStreamCodecs.enumCodec(PlayerTradeAction.class);
}
