package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.recipe.TalismanDrawingRecipe;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingScorer.Stroke;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * One finished stroke, in bitmap coordinates. The server measures it itself and answers with an acknowledgement;
 * a stroke that is refused has to be erased on the drawing side, or the final reconciliation cannot pass.
 */
public record TalismanStrokeC2SPayload(int containerId, Identifier recipeId, Stroke stroke)
        implements CustomPacketPayload {
    public static final Type<TalismanStrokeC2SPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "talisman_stroke_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TalismanStrokeC2SPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TalismanStrokeC2SPayload::containerId,
            Identifier.STREAM_CODEC, TalismanStrokeC2SPayload::recipeId,
            ByteBufCodecs.fromCodecWithRegistries(TalismanDrawingRecipe.STROKE_CODEC), TalismanStrokeC2SPayload::stroke,
            TalismanStrokeC2SPayload::new);

    @Override
    public @NonNull Type<TalismanStrokeC2SPayload> type() {
        return TYPE;
    }
}
