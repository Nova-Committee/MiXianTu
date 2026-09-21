package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.runtime.wheel.WheelLayout;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/** A complete wheel layout, sent when the configuration screen closes; never a per-sector edit. */
public record WheelLayoutC2SPayload(WheelLayout layout) implements CustomPacketPayload {
    public static final Type<WheelLayoutC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "wheel_layout_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WheelLayoutC2SPayload> STREAM_CODEC = StreamCodec.composite(
            WheelLayout.STREAM_CODEC, WheelLayoutC2SPayload::layout,
            WheelLayoutC2SPayload::new);

    @Override
    public @NonNull Type<WheelLayoutC2SPayload> type() {
        return TYPE;
    }
}
