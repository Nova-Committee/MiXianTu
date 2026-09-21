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
 * Asks the server what an owner UUID is called.
 *
 * <p>An artifact stores its owner's name when it is claimed, so this is only for stacks claimed before that
 * name was kept, or written by a pack that set ownership some other way: a client can name the players its own
 * connection lists and nobody else, while the server keeps a name for everybody who has ever joined - the same
 * division Jade solves by gathering on the server and sending the answer down.</p>
 *
 * <p>It carries only the id, and the answer is a name that is already public on any server with a tab list, so
 * nothing here is a secret; asking is deliberately one question per id per session rather than one per frame.</p>
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
