package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/** A cheque table request: true signs deposited currency, false redeems a cheque. */
public record ChequeActionC2SPayload(boolean checkIn) implements CustomPacketPayload {
    public static final Type<ChequeActionC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "cheque_action_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChequeActionC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ChequeActionC2SPayload decode(RegistryFriendlyByteBuf buffer) {
            return new ChequeActionC2SPayload(buffer.readBoolean());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ChequeActionC2SPayload value) {
            buffer.writeBoolean(value.checkIn());
        }
    };

    @Override
    public @NonNull Type<ChequeActionC2SPayload> type() {
        return TYPE;
    }
}
