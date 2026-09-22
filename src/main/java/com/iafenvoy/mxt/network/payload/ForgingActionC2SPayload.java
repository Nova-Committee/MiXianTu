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

import java.util.Optional;

/**
 * A forging request. {@code definition} is the blueprint id for {@link ForgingAction#SELECT} and the method id for
 * {@link ForgingAction#STRIKE}; the other transitions carry none, because the server reads everything else from its
 * own session state. The request names no table: the server resolves it from the menu the sending player has open.
 */
public record ForgingActionC2SPayload(ForgingAction action,
                                      Optional<Identifier> definition) implements CustomPacketPayload {
    public static final Type<ForgingActionC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "forging_action_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ForgingActionC2SPayload> STREAM_CODEC = StreamCodec.composite(
            ForgingAction.STREAM_CODEC, ForgingActionC2SPayload::action,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), ForgingActionC2SPayload::definition,
            ForgingActionC2SPayload::new);

    public ForgingActionC2SPayload {
        if (action == null)
            throw new IllegalArgumentException("Forging payload values must not be null");
        if (action.requiresDefinition() != definition.isPresent()) {
            throw new IllegalArgumentException("Forging action " + action + " has an invalid definition argument");
        }
    }

    public static ForgingActionC2SPayload select(Identifier blueprint) {
        return new ForgingActionC2SPayload(ForgingAction.SELECT, Optional.of(blueprint));
    }

    public static ForgingActionC2SPayload strike(Identifier method) {
        return new ForgingActionC2SPayload(ForgingAction.STRIKE, Optional.of(method));
    }

    public static ForgingActionC2SPayload finish() {
        return new ForgingActionC2SPayload(ForgingAction.FINISH, Optional.empty());
    }

    public static ForgingActionC2SPayload cancel() {
        return new ForgingActionC2SPayload(ForgingAction.CANCEL, Optional.empty());
    }

    @Override
    public @NonNull Type<ForgingActionC2SPayload> type() {
        return TYPE;
    }

    public enum ForgingAction {
        SELECT(true),
        STRIKE(true),
        FINISH(false),
        // Returns the locked materials to the player.
        CANCEL(false);

        public static final StreamCodec<ByteBuf, ForgingAction> STREAM_CODEC = MiscStreamCodecs.enumCodec(ForgingAction.class);
        private final boolean requiresDefinition;

        ForgingAction(boolean requiresDefinition) {
            this.requiresDefinition = requiresDefinition;
        }

        public boolean requiresDefinition() {
            return this.requiresDefinition;
        }
    }
}
