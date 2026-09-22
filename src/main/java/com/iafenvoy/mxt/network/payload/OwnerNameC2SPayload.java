package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

/**
 * Asks the server what an owner UUID is called. An artifact stores its owner's name when it is claimed, so this is
 * only for stacks claimed before that name was kept or written by a pack that set ownership another way: a client
 * can name the players on its own connection and nobody else, while the server keeps a name for everybody who has
 * ever joined. Asking is one question per id per session rather than one per frame.
 */
public record OwnerNameC2SPayload(UUID owner) implements CustomPacketPayload {
    public static final Type<OwnerNameC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "owner_name_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerNameC2SPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, OwnerNameC2SPayload::owner,
            OwnerNameC2SPayload::new);

    @Override
    public @NonNull Type<OwnerNameC2SPayload> type() {
        return TYPE;
    }
}
