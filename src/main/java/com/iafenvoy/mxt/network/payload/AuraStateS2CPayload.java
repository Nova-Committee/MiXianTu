package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Server-authoritative aura source and resolved concentration for the local player.
 */
public record AuraStateS2CPayload(Identifier source, double concentration, double maximum) implements CustomPacketPayload {
    public static final Type<AuraStateS2CPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "aura_state_s2c"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AuraStateS2CPayload> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, AuraStateS2CPayload::source,
            ByteBufCodecs.DOUBLE, AuraStateS2CPayload::concentration,
            ByteBufCodecs.DOUBLE, AuraStateS2CPayload::maximum,
            AuraStateS2CPayload::new
    );

    @Override
    public @NonNull Type<AuraStateS2CPayload> type() {
        return TYPE;
    }
}
