package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.recipe.TalismanDrawingRecipe;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingScorer;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingScorer.Stroke;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Opens one drawing for one player: the shape to trace, how the reference layer is shown and everything the local
 * preview needs. The scoring parameters travel only when the formula allows a preview; the recipe itself, the
 * result block and the canvas size never do.
 *
 * <p>The two preprocessing constants are code constants on both sides and are deliberately not sent.
 */
public record TalismanDrawingStartS2CPayload(int containerId, Identifier recipeId, List<Stroke> strokes,
                                             String guide, boolean showOrder, double tolerance,
                                             TalismanDrawingScorer.Judgement judgement,
                                             int minStrokeInterval) implements CustomPacketPayload {
    public static final Type<TalismanDrawingStartS2CPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "talisman_drawing_start_s2c"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TalismanDrawingStartS2CPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TalismanDrawingStartS2CPayload::containerId,
            Identifier.STREAM_CODEC, TalismanDrawingStartS2CPayload::recipeId,
            ByteBufCodecs.fromCodecWithRegistries(TalismanDrawingRecipe.STROKE_CODEC).apply(ByteBufCodecs.list(64)),
            TalismanDrawingStartS2CPayload::strokes,
            ByteBufCodecs.STRING_UTF8, TalismanDrawingStartS2CPayload::guide,
            ByteBufCodecs.BOOL, TalismanDrawingStartS2CPayload::showOrder,
            ByteBufCodecs.DOUBLE, TalismanDrawingStartS2CPayload::tolerance,
            ByteBufCodecs.fromCodecWithRegistries(TalismanDrawingRecipe.JUDGEMENT_CODEC), TalismanDrawingStartS2CPayload::judgement,
            ByteBufCodecs.VAR_INT, TalismanDrawingStartS2CPayload::minStrokeInterval,
            TalismanDrawingStartS2CPayload::new);

    @Override
    public @NonNull Type<TalismanDrawingStartS2CPayload> type() {
        return TYPE;
    }
}
