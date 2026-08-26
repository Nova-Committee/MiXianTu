package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/** Requests a server-authoritative cultivation-mode toggle. */
public enum CultivationToggleC2SPayload implements CustomPacketPayload {
    INSTANCE;

    public static final Type<CultivationToggleC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "cultivation_toggle_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CultivationToggleC2SPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public @NonNull Type<CultivationToggleC2SPayload> type() {
        return TYPE;
    }
}
