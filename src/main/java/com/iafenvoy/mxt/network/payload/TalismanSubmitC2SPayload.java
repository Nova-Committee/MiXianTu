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

import java.util.List;

/**
 * The whole drawing, sent once when the player submits. It exists to be reconciled against the strokes that
 * arrived one at a time: anything that does not match costs the session, which is what a drawing prepared
 * offline cannot survive.
 */
public record TalismanSubmitC2SPayload(int containerId, Identifier recipeId, List<Stroke> strokes)
        implements CustomPacketPayload {
    public static final int MAX_STROKES = 64;
    public static final Type<TalismanSubmitC2SPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "talisman_submit_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TalismanSubmitC2SPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TalismanSubmitC2SPayload::containerId,
            Identifier.STREAM_CODEC, TalismanSubmitC2SPayload::recipeId,
            ByteBufCodecs.fromCodecWithRegistries(TalismanDrawingRecipe.STROKE_CODEC).apply(ByteBufCodecs.list(MAX_STROKES)),
            TalismanSubmitC2SPayload::strokes,
            TalismanSubmitC2SPayload::new);

    @Override
    public @NonNull Type<TalismanSubmitC2SPayload> type() {
        return TYPE;
    }
}
