package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * A forging request uses an enum because it has more than two possible state transitions.
 */
public record ForgingActionC2SPayload(BlockPos position, ForgingAction action,
                                      Optional<Identifier> definition) implements CustomPacketPayload {
    public static final Type<ForgingActionC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "forging_action_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ForgingActionC2SPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ForgingActionC2SPayload decode(RegistryFriendlyByteBuf buffer) {
            BlockPos position = buffer.readBlockPos();
            ForgingAction action = buffer.readEnum(ForgingAction.class);
            Optional<Identifier> definition = buffer.readBoolean() ? Optional.of(PayloadCodecs.readIdentifier(buffer)) : Optional.empty();
            return new ForgingActionC2SPayload(position, action, definition);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ForgingActionC2SPayload value) {
            buffer.writeBlockPos(value.position());
            buffer.writeEnum(value.action());
            buffer.writeBoolean(value.definition().isPresent());
            value.definition().ifPresent(id -> PayloadCodecs.writeIdentifier(buffer, id));
        }
    };

    public ForgingActionC2SPayload {
        if (position == null || action == null)
            throw new IllegalArgumentException("Forging payload values must not be null");
        if ((action == ForgingAction.CANCEL) != definition.isEmpty()) {
            throw new IllegalArgumentException("Only a cancel request may omit its forging definition");
        }
    }

    public static ForgingActionC2SPayload start(BlockPos position, Identifier blueprint) {
        return new ForgingActionC2SPayload(position, ForgingAction.START, Optional.of(blueprint));
    }

    public static ForgingActionC2SPayload strike(BlockPos position, Identifier method) {
        return new ForgingActionC2SPayload(position, ForgingAction.STRIKE, Optional.of(method));
    }

    public static ForgingActionC2SPayload finish(BlockPos position, Identifier blueprint) {
        return new ForgingActionC2SPayload(position, ForgingAction.FINISH, Optional.of(blueprint));
    }

    public static ForgingActionC2SPayload cancel(BlockPos position) {
        return new ForgingActionC2SPayload(position, ForgingAction.CANCEL, Optional.empty());
    }

    @Override
    public @NonNull Type<ForgingActionC2SPayload> type() {
        return TYPE;
    }
}
