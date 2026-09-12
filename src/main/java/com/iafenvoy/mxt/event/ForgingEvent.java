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
 * Server-side hooks for the complete forging transaction.
 *
 * <h2>What a listener is given</h2>
 * The player who asked for the operation and the position of the table it is happening at, then the
 * payload of the phase. The session arrives as a {@link ForgingSessionView} rather than as the session
 * itself: a listener reads the value, the steps, the history and whether the piece would settle, and
 * cannot edit any of it. {@code research/06} asks for exactly that - a pre event may cancel the operation
 * or replace the strike's costs, and the session's value, history and quality are not its to touch.
 *
 * <h2>What happens when a listener throws</h2>
 * NeoForge's bus logs a listener's throwable and rethrows it, and these events are posted in the middle of
 * an operation: before anything is taken for {@code Start}, after the precheck and before the payment for
 * {@code StrikePre}, before the settlement for {@code CompletePre}. An exception escaping from one of those
 * would not be a refusal, it would be a half-performed action - resources paid for a strike the table never
 * recorded. The service therefore catches it at the post site and reports
 * {@code ForgingService.Failure.LISTENER_ERROR} instead, which the workstation treats exactly like a
 * cancellation: the operation is refused and the session is left as it was. On the notification events -
 * {@code Started}, {@code StrikePost}, {@code CompletePost} - there is nothing left to refuse, so a throw
 * there is logged and the operation continues.
 */
public abstract class ForgingEvent extends Event {
    private final ServerPlayer player;
    private final BlockPos pos;

    protected ForgingEvent(ServerPlayer player, BlockPos pos) {
        this.player = player;
        this.pos = pos;
    }

    /**
     * Who asked for this operation.
     *
     * <p>Not a security boundary and not the authority on anything - the operation is performed by the
     * server against its own state - but it is what a listener needs to react as something other than a
     * global rule.</p>
     */
    public ServerPlayer player() {
        return this.player;
    }

    /**
     * The table the operation is happening at.
     *
     * <p>The position rather than the surface, so that a listener can name the table without being handed
     * its writable container and session state - see {@link ForgingSessionView} for the same reasoning
     * applied to the session.</p>
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
