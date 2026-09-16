package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.artifact.ForgingResultComponent;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingMethod;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.runtime.forging.ForgingSessionView;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

import java.util.LinkedList;
import java.util.List;

/**
 * Server-side hooks for the complete forging transaction, posted with the requesting player and the table
 * position. The session arrives as a read-only {@link ForgingSessionView}: a pre event may cancel or replace the
 * strike's costs, but not the session's value, history or quality. A listener throw on a pre event is caught as
 * {@code ForgingService.Failure.LISTENER_ERROR} and leaves the session as it was; elsewhere it is only logged.
 */
public abstract class ForgingEvent extends Event {
    private final ServerPlayer player;
    private final BlockPos pos;

    protected ForgingEvent(ServerPlayer player, BlockPos pos) {
        this.player = player;
        this.pos = pos;
    }

    /**
     * Who asked for this operation. Not a security boundary - the server acts against its own state - but what
     * a listener needs to react as something other than a global rule.
     */
    public ServerPlayer player() {
        return this.player;
    }

    /**
     * The table the operation is happening at: the position rather than the surface, so a listener can name
     * the table without being handed its writable container and session state - see {@link ForgingSessionView}.
     */
    public BlockPos pos() {
        return this.pos;
    }

    public static final class Start extends ForgingEvent implements ICancellableEvent {
        private final ForgingBlueprint blueprint;

        public Start(ServerPlayer player, BlockPos pos, ForgingBlueprint blueprint) {
            super(player, pos);
            this.blueprint = blueprint;
        }

        public ForgingBlueprint blueprint() {
            return this.blueprint;
        }
    }

    public static final class Cancel extends ForgingEvent implements ICancellableEvent {
        private final ForgingSessionView session;

        public Cancel(ServerPlayer player, BlockPos pos, ForgingSessionView session) {
            super(player, pos);
            this.session = session;
        }

        public ForgingSessionView session() {
            return this.session;
        }
    }

    public static final class Started extends ForgingEvent {
        private final ForgingSessionView session;

        public Started(ServerPlayer player, BlockPos pos, ForgingSessionView session) {
            super(player, pos);
            this.session = session;
        }

        public ForgingSessionView session() {
            return this.session;
        }
    }

    public static final class StrikePre extends ForgingEvent implements ICancellableEvent {
        private final ForgingSessionView session;
        private final Holder<ForgingMethod> method;
        private final ResourceHolderAttachment resources;
        private final FormulaContext context;
        private List<ResourceCost> costs;

        public StrikePre(ServerPlayer player, BlockPos pos, ForgingSessionView session, Holder<ForgingMethod> method,
                         ResourceHolderAttachment resources, FormulaContext context) {
            super(player, pos);
            this.session = session;
            this.method = method;
            this.resources = resources;
            this.context = context;
            this.costs = new LinkedList<>(method.value().costs());
        }

        public ForgingSessionView session() {
            return this.session;
        }

        public Holder<ForgingMethod> method() {
            return this.method;
        }

        public ResourceHolderAttachment resources() {
            return this.resources;
        }

        public FormulaContext context() {
            return this.context;
        }

        public List<ResourceCost> costs() {
            return this.costs;
        }

        public void setCosts(List<ResourceCost> costs) {
            this.costs = new LinkedList<>(costs);
        }
    }

    public static final class StrikePost extends ForgingEvent {
        private final ForgingSessionView session;

        public StrikePost(ServerPlayer player, BlockPos pos, ForgingSessionView session) {
            super(player, pos);
            this.session = session;
        }

        public ForgingSessionView session() {
            return this.session;
        }
    }

    public static final class CompletePre extends ForgingEvent implements ICancellableEvent {
        private final Holder<ForgingBlueprint> blueprint;
        private final ForgingSessionView session;

        public CompletePre(ServerPlayer player, BlockPos pos, Holder<ForgingBlueprint> blueprint, ForgingSessionView session) {
            super(player, pos);
            this.blueprint = blueprint;
            this.session = session;
        }

        public Holder<ForgingBlueprint> blueprint() {
            return this.blueprint;
        }

        public ForgingSessionView session() {
            return this.session;
        }
    }

    public static final class CompletePost extends ForgingEvent {
        private final Holder<ForgingBlueprint> blueprint;
        private final ForgingSessionView session;
        private final ForgingResultComponent result;

        public CompletePost(ServerPlayer player, BlockPos pos, Holder<ForgingBlueprint> blueprint, ForgingSessionView session,
                            ForgingResultComponent result) {
            super(player, pos);
            this.blueprint = blueprint;
            this.session = session;
            this.result = result;
        }

        public Holder<ForgingBlueprint> blueprint() {
            return this.blueprint;
        }

        public ForgingSessionView session() {
            return this.session;
        }

        public ForgingResultComponent result() {
            return this.result;
        }
    }
}
