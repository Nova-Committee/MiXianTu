package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.artifact.ForgingResultComponent;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingMethod;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.runtime.forging.ForgingSession;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

import java.util.LinkedList;
import java.util.List;

/**
 * Server-side hooks for the complete forging transaction.
 */
public abstract class ForgingEvent extends Event {
    public static final class Start extends ForgingEvent implements ICancellableEvent {
        private final ForgingBlueprint blueprint;

        public Start(ForgingBlueprint blueprint) {
            this.blueprint = blueprint;
        }

        public ForgingBlueprint blueprint() {
            return this.blueprint;
        }
    }

    public static final class Cancel extends ForgingEvent implements ICancellableEvent {
        private final ForgingSession session;

        public Cancel(ForgingSession session) {
            this.session = session;
        }

        public ForgingSession session() {
            return this.session;
        }
    }

    public static final class Started extends ForgingEvent {
        private final ForgingSession session;

        public Started(ForgingSession session) {
            this.session = session;
        }

        public ForgingSession session() {
            return this.session;
        }
    }

    public static final class StrikePre extends ForgingEvent implements ICancellableEvent {
        private final ForgingSession session;
        private final Holder<ForgingMethod> method;
        private final ResourceHolderAttachment resources;
        private final FormulaContext context;
        private List<ResourceCost> costs;

        public StrikePre(ForgingSession session, Holder<ForgingMethod> method, ResourceHolderAttachment resources, FormulaContext context) {
            this.session = session;
            this.method = method;
            this.resources = resources;
            this.context = context;
            this.costs = new LinkedList<>(method.value().costs());
        }

        public ForgingSession session() {
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
        private final ForgingSession session;

        public StrikePost(ForgingSession session) {
            this.session = session;
        }

        public ForgingSession session() {
            return this.session;
        }
    }

    public static final class CompletePre extends ForgingEvent implements ICancellableEvent {
        private final Holder<ForgingBlueprint> blueprint;
        private final ForgingSession session;

        public CompletePre(Holder<ForgingBlueprint> blueprint, ForgingSession session) {
            this.blueprint = blueprint;
            this.session = session;
        }

        public Holder<ForgingBlueprint> blueprint() {
            return this.blueprint;
        }

        public ForgingSession session() {
            return this.session;
        }
    }

    public static final class CompletePost extends ForgingEvent {
        private final Holder<ForgingBlueprint> blueprint;
        private final ForgingSession session;
        private final ForgingResultComponent result;

        public CompletePost(Holder<ForgingBlueprint> blueprint, ForgingSession session, ForgingResultComponent result) {
            this.blueprint = blueprint;
            this.session = session;
            this.result = result;
        }

        public Holder<ForgingBlueprint> blueprint() {
            return this.blueprint;
        }

        public ForgingSession session() {
            return this.session;
        }

        public ForgingResultComponent result() {
            return this.result;
        }
    }
}
