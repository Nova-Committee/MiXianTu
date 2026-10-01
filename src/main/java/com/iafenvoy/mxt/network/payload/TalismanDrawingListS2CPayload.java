package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * The formulas a workstation offers one player. The names are computed on the server: a 26.1 client has no recipe
 * table, so it cannot resolve the talisman a formula points at.
 */
public record TalismanDrawingListS2CPayload(int containerId, List<Row> rows) implements CustomPacketPayload {
    public static final int MAX_ROWS = 256;
    public static final Type<TalismanDrawingListS2CPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "talisman_drawing_list_s2c"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TalismanDrawingListS2CPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TalismanDrawingListS2CPayload::containerId,
            Row.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ROWS)), TalismanDrawingListS2CPayload::rows,
            TalismanDrawingListS2CPayload::new);

    @Override
    public @NonNull Type<TalismanDrawingListS2CPayload> type() {
        return TYPE;
    }

    /**
     * One row: which formula, what to call it and whether it can be started right now.
     */
    public record Row(Identifier id, Component name, boolean affordable) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Row::id,
                ComponentSerialization.STREAM_CODEC, Row::name,
                ByteBufCodecs.BOOL, Row::affordable,
                Row::new);
    }
}
