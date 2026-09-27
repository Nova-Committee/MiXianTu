package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.util.codec.MiscStreamCodecs;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record AlchemyActionC2SPayload(int containerId, Action action, double temperature) implements CustomPacketPayload {
    public static final Type<AlchemyActionC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "alchemy_action_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AlchemyActionC2SPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AlchemyActionC2SPayload::containerId,
            Action.STREAM_CODEC, AlchemyActionC2SPayload::action,
            ByteBufCodecs.DOUBLE, AlchemyActionC2SPayload::temperature,
            AlchemyActionC2SPayload::new);

    @Override
    public @NonNull Type<AlchemyActionC2SPayload> type() {
        return TYPE;
    }

    public enum Action {
        TEMPERATURE, START, ABORT;

        public static final StreamCodec<ByteBuf, Action> STREAM_CODEC = MiscStreamCodecs.enumCodec(Action.class);
    }
}
