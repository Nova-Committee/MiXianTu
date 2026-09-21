package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Answers {@link OwnerNameC2SPayload} with the name the server has for that id, or with nothing when it has
 * never seen the player.
 *
 * <p>Nothing is a real answer rather than a failure: it means the tooltip keeps showing the id, which is what it
 * would have shown anyway. The client remembers the answer - including that one - so a name is asked for once
 * per session instead of once per rendered frame.</p>
 */
public record OwnerNameS2CPayload(UUID owner, Optional<String> name) implements CustomPacketPayload {
    public static final Type<OwnerNameS2CPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "owner_name_s2c"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerNameS2CPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, OwnerNameS2CPayload::owner,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), OwnerNameS2CPayload::name,
            OwnerNameS2CPayload::new);

    @Override
    public @NonNull Type<OwnerNameS2CPayload> type() {
        return TYPE;
    }
}
