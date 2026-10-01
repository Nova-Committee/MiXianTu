package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

/**
 * How a drawing ended: the completion the server computed, whether a grade was reached, the product itself (a
 * finished carrier, which this side never reconstructs) and how much pigment went into it.
 *
 * <p>{@code kind} tells a drawing the player cancelled (nothing was settled, so there is no completion to show)
 * from a settled one and from a session the server failed on its own checks.
 */
public record TalismanResultS2CPayload(int containerId, int kind, double completion, boolean success,
                                       Component text, ItemStack product, int pigmentSpent)
        implements CustomPacketPayload {
    /**
     * Cancelled by the player: the drawing ended without a settlement.
     */
    public static final int CANCELLED = 0;
    /**
     * Settled: {@code success} says whether a grade was reached.
     */
    public static final int SETTLED = 1;
    /**
     * Failed on the server's own checks (too many refused strokes), so no completion exists.
     */
    public static final int FAILED = 2;
    public static final Type<TalismanResultS2CPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "talisman_result_s2c"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TalismanResultS2CPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TalismanResultS2CPayload::containerId,
            ByteBufCodecs.VAR_INT, TalismanResultS2CPayload::kind,
            ByteBufCodecs.DOUBLE, TalismanResultS2CPayload::completion,
            ByteBufCodecs.BOOL, TalismanResultS2CPayload::success,
            ComponentSerialization.STREAM_CODEC, TalismanResultS2CPayload::text,
            // A failure carries no product, and ItemStack.STREAM_CODEC refuses an empty one.
            ItemStack.OPTIONAL_STREAM_CODEC, TalismanResultS2CPayload::product,
            ByteBufCodecs.VAR_INT, TalismanResultS2CPayload::pigmentSpent,
            TalismanResultS2CPayload::new);

    @Override
    public @NonNull Type<TalismanResultS2CPayload> type() {
        return TYPE;
    }
}
