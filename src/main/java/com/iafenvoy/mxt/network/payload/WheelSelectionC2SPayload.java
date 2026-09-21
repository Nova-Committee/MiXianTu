package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.runtime.wheel.WheelSlot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Tells the server which entry the player has armed, or that nothing is armed. It carries the entry, not a
 * sector number, and the server re-resolves the id: one that no longer resolves is stored as "nothing armed".
 */
public record WheelSelectionC2SPayload(Optional<WheelSlot> selection) implements CustomPacketPayload {
    public static final Type<WheelSelectionC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "wheel_selection_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WheelSelectionC2SPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(WheelSlot.STREAM_CODEC), WheelSelectionC2SPayload::selection,
            WheelSelectionC2SPayload::new);

    @Override
    public @NonNull Type<WheelSelectionC2SPayload> type() {
        return TYPE;
    }
}
