package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * "The player chose this entry, off this page": the one channel every wheel trigger travels on, resolved
 * server-side. The page is part of the request because the server re-reads that page and refuses the trigger when the
 * page no longer holds the entry.
 *
 * <p>The page and the kind travel as ids rather than as registered objects, so a page or a kind this side does not
 * know is refused by the server with a message instead of failing the packet - which is what lets a content mod add
 * either without a protocol version of its own.
 *
 * <p>{@code enabled} is the directed form of the same request: a screen or a script that wants one named state
 * instead of letting the server read the current one fills it in, and the entry is then addressed by id alone - there
 * is no cell behind such a request. The wheel itself always sends an empty one, because which way a switch goes is
 * the server's question to the implementation.
 */
public record WheelActionC2SPayload(Identifier source, Identifier kind, Identifier id,
                                    Optional<Boolean> enabled) implements CustomPacketPayload {
    public static final Type<WheelActionC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "wheel_action_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WheelActionC2SPayload> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, WheelActionC2SPayload::source,
            Identifier.STREAM_CODEC, WheelActionC2SPayload::kind,
            Identifier.STREAM_CODEC, WheelActionC2SPayload::id,
            ByteBufCodecs.optional(ByteBufCodecs.BOOL), WheelActionC2SPayload::enabled,
            WheelActionC2SPayload::new);

    // What a cell press sends: the direction, if the entry has one, is read on the server.
    public static WheelActionC2SPayload press(Identifier source, Identifier kind, Identifier id) {
        return new WheelActionC2SPayload(source, kind, id, Optional.empty());
    }

    @Override
    public @NonNull Type<WheelActionC2SPayload> type() {
        return TYPE;
    }
}
