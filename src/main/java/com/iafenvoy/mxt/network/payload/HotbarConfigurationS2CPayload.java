package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Requests that the client open the configuration screen for a hotbar mode.
 */
public record HotbarConfigurationS2CPayload(Identifier mode) implements CustomPacketPayload {
    public static final Type<HotbarConfigurationS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "hotbar_configuration_s2c"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HotbarConfigurationS2CPayload> STREAM_CODEC =
            StreamCodec.composite(Identifier.STREAM_CODEC, HotbarConfigurationS2CPayload::mode,
                    HotbarConfigurationS2CPayload::new);

    @Override
    public @NonNull Type<HotbarConfigurationS2CPayload> type() {
        return TYPE;
    }
}
