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
 * <p>{@code kind} tells a drawing the player cancelled with nothing drawn (nothing was settled, so there is no
 * completion to show) from a settled one, from a session the server failed on its own checks, and from a drawing
 * abandoned after a stroke - which is judged failed like a refusal, so its materials are gone.
 */
public record TalismanResultS2CPayload(int containerId, int kind, double completion, boolean success,
                                       Component text, ItemStack product, int pigmentSpent)
        implements CustomPacketPayload {
    /**
     * Cancelled with nothing drawn: the drawing ended without a settlement and its materials were handed back.
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
    /**
     * Abandoned after a stroke - by the cancel button, or by closing the screen. Judged failed: the materials are
     * gone and the recipe's failure action ran, so there is no completion to show either.
     */
    public static final int ABANDONED = 3;
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
