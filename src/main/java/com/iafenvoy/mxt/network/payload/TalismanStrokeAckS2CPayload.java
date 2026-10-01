package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * The answer to one stroke: taken or refused, why, and how much pigment it cost. A refusal is a normal answer -
 * "too fast", "not enough pigment" and "the brush is no longer on the cursor" are all things a player does.
 */
public record TalismanStrokeAckS2CPayload(int containerId, int index, boolean accepted, int refusal, int charged)
        implements CustomPacketPayload {
    /**
     * No refusal at all, which is what an accepted stroke carries.
     */
    public static final int ACCEPTED = -1;
    public static final Type<TalismanStrokeAckS2CPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "talisman_stroke_ack_s2c"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TalismanStrokeAckS2CPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TalismanStrokeAckS2CPayload::containerId,
            ByteBufCodecs.VAR_INT, TalismanStrokeAckS2CPayload::index,
            ByteBufCodecs.BOOL, TalismanStrokeAckS2CPayload::accepted,
            ByteBufCodecs.VAR_INT, TalismanStrokeAckS2CPayload::refusal,
            ByteBufCodecs.VAR_INT, TalismanStrokeAckS2CPayload::charged,
            TalismanStrokeAckS2CPayload::new);

    @Override
    public @NonNull Type<TalismanStrokeAckS2CPayload> type() {
        return TYPE;
    }
}
