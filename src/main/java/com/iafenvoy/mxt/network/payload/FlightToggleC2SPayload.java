package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Requests one of the two flight states of a named ability; the server still resolves the ability, re-checks that
 * a carrier offers it and decides whether the request matches the state it is in. The wheel does not use this -
 * it sends a press and lets the server read the state - so this remains the entry point for a script or a screen
 * that asks for one specific direction.
 */
public record FlightToggleC2SPayload(Identifier ability, boolean enabled) implements CustomPacketPayload {
    public static final Type<FlightToggleC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "flight_toggle_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FlightToggleC2SPayload> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, FlightToggleC2SPayload::ability,
            ByteBufCodecs.BOOL, FlightToggleC2SPayload::enabled,
            FlightToggleC2SPayload::new);

    @Override
    public @NonNull Type<FlightToggleC2SPayload> type() {
        return TYPE;
    }
}
