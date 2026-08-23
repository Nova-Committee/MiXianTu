package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Requests exchanging the main-hand stack with the first physical Curios back slot.
 */
public enum BackSlotSwapC2SPayload implements CustomPacketPayload {
    INSTANCE;
    public static final Type<BackSlotSwapC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "back_slot_swap_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BackSlotSwapC2SPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public @NonNull Type<BackSlotSwapC2SPayload> type() {
        return TYPE;
    }
}
