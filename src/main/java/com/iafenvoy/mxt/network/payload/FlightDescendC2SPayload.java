package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Whether the descend key is held, sent on every change: the key is not a vanilla input, so this is the only way the
 * server hears it.
 */
public record FlightDescendC2SPayload(boolean pressed) implements CustomPacketPayload {
    public static final Type<FlightDescendC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "flight_descend_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FlightDescendC2SPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, FlightDescendC2SPayload::pressed,
            FlightDescendC2SPayload::new);

    @Override
    public @NonNull Type<FlightDescendC2SPayload> type() {
        return TYPE;
    }
}
