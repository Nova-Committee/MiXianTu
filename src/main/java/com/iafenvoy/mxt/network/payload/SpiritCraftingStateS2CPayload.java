package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.api.AuraAccess;
import com.iafenvoy.mxt.data.aura.Aura;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public record SpiritCraftingStateS2CPayload(int containerId, List<Row> rows) implements CustomPacketPayload {
    public static final int MAX_ROWS = 8;
    public static final Type<SpiritCraftingStateS2CPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "spirit_crafting_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpiritCraftingStateS2CPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SpiritCraftingStateS2CPayload::containerId,
            ByteBufCodecs.collection(ArrayList::new, Row.STREAM_CODEC, MAX_ROWS), SpiritCraftingStateS2CPayload::rows,
            SpiritCraftingStateS2CPayload::new);

    public SpiritCraftingStateS2CPayload {
        rows = List.copyOf(rows);
        if (rows.size() > MAX_ROWS) throw new IllegalArgumentException("Too many crafting progress rows");
    }

    public record Row(Holder<Aura> aura, double stored, double required) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC = StreamCodec.composite(
                Aura.STREAM_CODEC, Row::aura,
                ByteBufCodecs.DOUBLE, Row::stored,
                ByteBufCodecs.DOUBLE, Row::required,
                Row::new);

        public Row {
            AuraAccess.requireNonNegative(stored);
            AuraAccess.requireNonNegative(required);
        }
    }

    @Override
    public @NonNull Type<SpiritCraftingStateS2CPayload> type() {
        return TYPE;
    }
}
