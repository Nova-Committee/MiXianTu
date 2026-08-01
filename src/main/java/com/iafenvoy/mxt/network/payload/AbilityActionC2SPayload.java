package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/**
 * Uses or cancels one ability. {@code cancel} is the complete two-state action model.
 */
public record AbilityActionC2SPayload(Identifier ability, boolean cancel) implements CustomPacketPayload {
    public static final Type<AbilityActionC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "ability_action_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AbilityActionC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AbilityActionC2SPayload decode(RegistryFriendlyByteBuf buffer) {
            return new AbilityActionC2SPayload(PayloadCodecs.readIdentifier(buffer), buffer.readBoolean());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, AbilityActionC2SPayload value) {
            PayloadCodecs.writeIdentifier(buffer, value.ability());
            buffer.writeBoolean(value.cancel());
        }
    };

    public static AbilityActionC2SPayload use(Identifier ability) {
        return new AbilityActionC2SPayload(ability, false);
    }

    public static AbilityActionC2SPayload cancel(Identifier ability) {
        return new AbilityActionC2SPayload(ability, true);
    }

    @Override
    public @NonNull Type<AbilityActionC2SPayload> type() {
        return TYPE;
    }
}
