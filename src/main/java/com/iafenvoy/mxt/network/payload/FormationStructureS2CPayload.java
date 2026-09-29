package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.Formation.RequiredBlock;
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
 * Carries a formation's declared shape to the player who asked to see it. The blocks travel rather than the id,
 * because a {@code structure_template} is server-side data and the client may not hold the same packs.
 */
public record FormationStructureS2CPayload(Component title,
                                           List<RequiredBlock> structure) implements CustomPacketPayload {
    public static final int MAX_BLOCKS = 4096;
    public static final Type<FormationStructureS2CPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "formation_structure_s2c"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FormationStructureS2CPayload> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, FormationStructureS2CPayload::title,
            ByteBufCodecs.fromCodecWithRegistries(RequiredBlock.CODEC).apply(ByteBufCodecs.list(MAX_BLOCKS)),
            FormationStructureS2CPayload::structure,
            FormationStructureS2CPayload::new);

    @Override
    public @NonNull Type<FormationStructureS2CPayload> type() {
        return TYPE;
    }
}
