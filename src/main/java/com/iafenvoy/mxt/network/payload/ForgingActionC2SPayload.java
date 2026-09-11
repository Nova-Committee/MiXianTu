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
 * A forging request uses an enum because it has more than two possible state transitions.
 *
 * <p>{@code definition} is the blueprint id for {@link ForgingAction#SELECT} and the method id for
 * {@link ForgingAction#STRIKE}. Settlement, selection-independent actions and cancellation carry
 * no payload because the server reads everything else from its own session state.</p>
 *
 * <p>The request names no table. The client cannot reach the block - its menu's
 * {@code ContainerLevelAccess} is {@code NULL} - so it would have to be told the coordinates and the
 * server would then have to trust them back. The server instead resolves the table from the menu the
 * sending player has open, which also means a request can only ever act on the table that player is
 * actually standing at.</p>
 */
public record ForgingActionC2SPayload(ForgingAction action, Optional<Identifier> definition) implements CustomPacketPayload {
    public static final Type<ForgingActionC2SPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "forging_action_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ForgingActionC2SPayload> STREAM_CODEC = StreamCodec.composite(
            ForgingAction.STREAM_CODEC, ForgingActionC2SPayload::action,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), ForgingActionC2SPayload::definition,
            ForgingActionC2SPayload::new
    );

    public ForgingActionC2SPayload {
        if (action == null)
            throw new IllegalArgumentException("Forging payload values must not be null");
        if (action.requiresDefinition() != definition.isPresent()) {
            throw new IllegalArgumentException("Forging action " + action + " has an invalid definition argument");
        }
    }

    /**
     * Selects the blueprint, which starts a session when the materials are present.
     */
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

    /**
     * Every supported state transition of one server-owned forging session.
     */
    public enum ForgingAction {
        /**
         * Opens a session for the given blueprint id.
         */
        SELECT(true),
        /**
         * Executes one strike with the given forging method id.
         */
        STRIKE(true),
        /**
         * Settles the active session.
         */
        FINISH(false),
        /**
         * Cancels the active session and returns the locked materials.
         */
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
