package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Picking a formula out of the list, which is what opens the drawing.
 */
public record TalismanSelectC2SPayload(int containerId, Identifier recipeId) implements CustomPacketPayload {
    public static final Type<TalismanSelectC2SPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "talisman_select_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TalismanSelectC2SPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TalismanSelectC2SPayload::containerId,
            Identifier.STREAM_CODEC, TalismanSelectC2SPayload::recipeId,
            TalismanSelectC2SPayload::new);

    @Override
    public @NonNull Type<TalismanSelectC2SPayload> type() {
        return TYPE;
    }
}
