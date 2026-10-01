package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Ending the drawing without submitting it. The session settles on the server, because what it took out of the
 * station slot has to be handed back or spent there and nowhere else.
 */
public record TalismanCancelC2SPayload(int containerId) implements CustomPacketPayload {
    public static final Type<TalismanCancelC2SPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "talisman_cancel_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TalismanCancelC2SPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TalismanCancelC2SPayload::containerId,
            TalismanCancelC2SPayload::new);

    @Override
    public @NonNull Type<TalismanCancelC2SPayload> type() {
        return TYPE;
    }
}
