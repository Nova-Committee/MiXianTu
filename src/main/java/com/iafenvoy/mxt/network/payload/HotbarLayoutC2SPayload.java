package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Sends a complete configurable-hotbar layout to the server when its screen closes.
 */
public record HotbarLayoutC2SPayload(Identifier mode, List<Identifier> slots) implements CustomPacketPayload {
    public static final Type<HotbarLayoutC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "hotbar_layout_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HotbarLayoutC2SPayload> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, HotbarLayoutC2SPayload::mode,
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), HotbarLayoutC2SPayload::slots,
            HotbarLayoutC2SPayload::new);

    @Override
    public @NonNull Type<HotbarLayoutC2SPayload> type() {
        return TYPE;
    }
}
