package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.runtime.wheel.WheelEntryKind;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/** "The player chose this sector": the one channel every wheel trigger travels on, resolved server-side. */
public record WheelActionC2SPayload(WheelEntryKind kind, Identifier id) implements CustomPacketPayload {
    public static final Type<WheelActionC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "wheel_action_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WheelActionC2SPayload> STREAM_CODEC = StreamCodec.composite(
            WheelEntryKind.STREAM_CODEC, WheelActionC2SPayload::kind,
            Identifier.STREAM_CODEC, WheelActionC2SPayload::id,
            WheelActionC2SPayload::new);

    @Override
    public @NonNull Type<WheelActionC2SPayload> type() {
        return TYPE;
    }
}
