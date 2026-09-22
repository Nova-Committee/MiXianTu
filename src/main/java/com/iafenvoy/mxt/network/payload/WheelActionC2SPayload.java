package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.runtime.wheel.WheelEntryKind;
import com.iafenvoy.mxt.runtime.wheel.WheelSource;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * "The player chose this entry, off this page": the one channel every wheel trigger travels on, resolved
 * server-side. The page is part of the request because the server re-reads that page and refuses the trigger when the
 * page no longer holds the entry.
 */
public record WheelActionC2SPayload(WheelSource source, WheelEntryKind kind, Identifier id) implements CustomPacketPayload {
    public static final Type<WheelActionC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "wheel_action_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WheelActionC2SPayload> STREAM_CODEC = StreamCodec.composite(
            WheelSource.STREAM_CODEC, WheelActionC2SPayload::source,
            WheelEntryKind.STREAM_CODEC, WheelActionC2SPayload::kind,
            Identifier.STREAM_CODEC, WheelActionC2SPayload::id,
            WheelActionC2SPayload::new);

    @Override
    public @NonNull Type<WheelActionC2SPayload> type() {
        return TYPE;
    }
}
