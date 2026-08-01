package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Requests one of the two flight states; the server still validates the requested archetype.
 */
public record FlightToggleC2SPayload(Identifier archetype, boolean enabled) implements CustomPacketPayload {
    public static final Type<FlightToggleC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "flight_toggle_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FlightToggleC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FlightToggleC2SPayload decode(RegistryFriendlyByteBuf buffer) {
            return new FlightToggleC2SPayload(PayloadCodecs.readIdentifier(buffer), buffer.readBoolean());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, FlightToggleC2SPayload value) {
            PayloadCodecs.writeIdentifier(buffer, value.archetype());
            buffer.writeBoolean(value.enabled());
        }
    };

    @Override
    public @NonNull Type<FlightToggleC2SPayload> type() {
        return TYPE;
    }
}
