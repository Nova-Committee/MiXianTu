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
 * Tells the server which cell the use key would spend while nothing is on screen, or that there is none. It
 * carries the <strong>number</strong> of the cell and nothing else: cells are numbered across the whole wheel,
 * so the number is all that has to be remembered, and it deliberately survives its entry going away - put the
 * item back and the same number finds the same slot again.
 *
 * <p>What that cell holds is not this channel's business. A trigger names the source and the entry, resolved
 * from the cell at the moment it is used.</p>
 */
public record WheelSelectionC2SPayload(Optional<Integer> armed) implements CustomPacketPayload {
    public static final Type<WheelSelectionC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "wheel_selection_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WheelSelectionC2SPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), WheelSelectionC2SPayload::armed,
            WheelSelectionC2SPayload::new);

    @Override
    public @NonNull Type<WheelSelectionC2SPayload> type() {
        return TYPE;
    }
}
