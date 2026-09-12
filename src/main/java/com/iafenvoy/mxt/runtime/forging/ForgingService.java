package com.iafenvoy.mxt.runtime.forging;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.artifact.ForgingResultComponent;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingMethod;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.event.ForgingEvent;
import com.iafenvoy.mxt.event.ForgingEvent.*;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Evaluation;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Result;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;

/**
 * Server-side coordinator for method validation, atomic payment and final forging-quality data.
 */
public final class ForgingService {
    private ForgingService() {
    }

    public static StartResult start(ServerPlayer player, ForgingSurface surface, ForgingBlueprint blueprint, RegistryAccess registries) {
        Failure refusal = postEvent(new Start(player, surface.pos(), blueprint));
        if (refusal != null) return StartResult.rejected(refusal);
        ForgingPlan plan;
        try {
            plan = blueprint.plan(registries);
        } catch (IllegalArgumentException exception) {
            return StartResult.rejected(Failure.INVALID_BLUEPRINT);
        }
        // After the try, and deliberately so: a listener of `Started` throwing an IllegalArgumentException
        // used to land in that catch and be reported as a broken blueprint.
        ForgingSession session = new ForgingSession(plan);
        notifyListeners(new Started(player, surface.pos(), new ForgingSessionView(session)));
        return StartResult.started(session);
    }

    public static StrikeResult strike(ServerPlayer player, ForgingSurface surface, ForgingSession session, Holder<ForgingMethod> method,
                                      ResourceHolderAttachment resources, FormulaContext context, BooleanSupplier conditions) {
        if (!conditions.getAsBoolean()) return StrikeResult.rejected(Failure.CONDITIONS, null);
        Identifier methodId = HolderHelper.id(method);
        if (!session.canStrike(methodId)) return StrikeResult.rejected(Failure.INVALID_STRIKE, null);
        StrikePre event = new StrikePre(player, surface.pos(), new ForgingSessionView(session), method, resources, context);
        Failure refusal = postEvent(event);
        if (refusal != null) return StrikeResult.rejected(refusal, null);
        Evaluation costs;
        try {
            costs = ResourceTransactions.evaluate(event.costs(), context);
        } catch (IllegalArgumentException exception) {
            return StrikeResult.rejected(Failure.INVALID_FORMULA, null);
        }
        Result payment = ResourceTransactions.tryConsume(resources, costs);
        if (!payment.committed()) return StrikeResult.rejected(Failure.INSUFFICIENT_RESOURCE, payment.failedResource());
        if (!session.strike(methodId))
            throw new IllegalStateException("Forging session changed after its strike precheck");
        notifyListeners(new StrikePost(player, surface.pos(), new ForgingSessionView(session)));
        return StrikeResult.struck(session.value(), session.steps(), payment.amounts());
    }

    public static FinishResult finish(ServerPlayer player, ForgingSurface surface, Holder<ForgingBlueprint> blueprint, ForgingSession session) {
        return finish(player, surface, blueprint, session, blueprint.value()::qualityFor);
    }

    public static FinishResult finish(ServerPlayer player, ForgingSurface surface, Holder<ForgingBlueprint> blueprint, ForgingSession session,
                                      IntFunction<Holder<ItemQuality>> qualityForExtraSteps) {
        Failure refusal = postEvent(new CompletePre(player, surface.pos(), blueprint, new ForgingSessionView(session)));
        if (refusal != null) return FinishResult.rejected(refusal);
        if (!session.canComplete()) return FinishResult.rejected(Failure.NOT_COMPLETE);
        int extra = session.extraSteps();
        Holder<ItemQuality> quality = qualityForExtraSteps.apply(extra);
        if (quality == null) return FinishResult.rejected(Failure.INVALID_BLUEPRINT);
        ForgingResultComponent result = new ForgingResultComponent(HolderHelper.id(blueprint), session.value(), session.steps(), session.optimalSteps(), extra, quality);
        notifyListeners(new CompletePost(player, surface.pos(), blueprint, new ForgingSessionView(session), result));
        return FinishResult.finished(result);
    }

    /**
     * Cancels a session before its input is returned by the owning inventory adapter.
     *
     * @return the refusal to report - {@link Failure#CANCELLED} when a listener vetoed it, or
     * {@link Failure#LISTENER_ERROR} when one failed - or {@code null} when the cancellation stands
     */
    public static @Nullable Failure cancel(ServerPlayer player, ForgingSurface surface, ForgingSession session) {
        return postEvent(new Cancel(player, surface.pos(), new ForgingSessionView(session)));
    }

    /**
     * Posts a deciding event: one whose answer is whether the operation may go ahead.
     *
     * <p>The exception handling is the point of the helper. NeoForge's bus logs a listener's throwable and
     * then rethrows it, and every call above sits in the middle of a transaction - {@code Start} before
     * anything is taken, {@code StrikePre} after the precheck and before the payment, {@code CompletePre}
     * before the settlement. A throwable escaping from here would leave resources paid for a strike the
     * table never recorded, or a session that depends on how far the caller got. So a listener that throws
     * refuses the operation, under its own name, and the caller reports it the way it reports a
     * cancellation.</p>
     *
     * <p>{@link VirtualMachineError} is not a listener bug and is rethrown.</p>
     *
     * @return the refusal to report, or {@code null} when the operation may continue
     */
    static <T extends ForgingEvent & ICancellableEvent> @Nullable Failure postEvent(T event) {
        try {
            return NeoForge.EVENT_BUS.post(event).isCanceled() ? Failure.CANCELLED : null;
        } catch (VirtualMachineError error) {
            throw error;
        } catch (Throwable throwable) {
            MiXianTu.LOGGER.error("A {} listener threw; the forging operation is refused as {}",
                    event.getClass().getSimpleName(), Failure.LISTENER_ERROR, throwable);
            return Failure.LISTENER_ERROR;
        }
    }

    /**
     * Posts a notification: an event whose operation has already been applied.
     *
     * <p>Nothing is left to refuse by the time these go out - the strike is paid for and applied, the
     * result is built - so a listener that throws is logged and the call returns normally. That is the
     * difference from {@link #postEvent(ForgingEvent)}: there, a broken listener must not leave a
     * half-performed action; here, throwing would undo the player's own action on account of a
     * third-party script.</p>
     */
    static void notifyListeners(ForgingEvent event) {
        try {
            NeoForge.EVENT_BUS.post(event);
        } catch (VirtualMachineError error) {
            throw error;
        } catch (Throwable throwable) {
            MiXianTu.LOGGER.error("A {} listener threw; the forging operation had already been applied and stands",
                    event.getClass().getSimpleName(), throwable);
        }
    }

    public enum Failure {
        DISABLED,
        INVALID_BLUEPRINT,
        CONDITIONS,
        INVALID_STRIKE,
        INVALID_FORMULA,
        INSUFFICIENT_RESOURCE,
        NOT_COMPLETE,
        CANCELLED,
        /**
         * No forging session exists at the table.
         */
        NO_SESSION,
        /**
         * The table already owns a session.
         */
        ALREADY_ACTIVE,
        /**
         * The player is too far from the table.
         */
        OUT_OF_RANGE,
        /**
         * The blueprint is not provided by the placed blueprint items.
         */
        BLUEPRINT_NOT_HELD,
        /**
         * The struck method is not in the intersection of the blueprint's allowed methods and the
         * methods the placed tools unlock.
         */
        METHOD_NOT_AVAILABLE,
        /**
         * The input slots cannot cover the blueprint material list.
         */
        INSUFFICIENT_MATERIALS,
        /**
         * The output slot is occupied.
         */
        OUTPUT_BLOCKED,
        /**
         * The strike was refused by the method cooldown.
         */
        COOLDOWN,
        /**
         * A listener for the phase threw, so the operation was refused instead of half-performed.
         *
         * <p>Named apart from {@link #CANCELLED} because the two mean different things to whoever reads the
         * log: one is a script that said no, the other is a script that broke. What they share is where they
         * stop the operation - before it has done anything - which is what {@link #refusedByListener}
         * answers, and why the workstation leaves the session alone for both.</p>
         */
        LISTENER_ERROR;

        /**
         * Whether this failure is a listener refusing the request, rather than a verdict on the piece.
         *
         * <p>The two ways a listener can stop an operation are cancelling the event and throwing out of it,
         * and the service reports them as {@link #CANCELLED} and {@link #LISTENER_ERROR}. Neither has
         * consumed or decided anything, so a session they stop is left exactly as it was - which is the
         * opposite of {@link #NOT_COMPLETE}, a verdict that settles the session by failing it.</p>
         */
        public boolean refusedByListener() {
            return this == CANCELLED || this == LISTENER_ERROR;
        }
    }

    public record StartResult(ForgingSession session, Failure failure) {
        private static StartResult started(ForgingSession session) {
            return new StartResult(session, null);
        }

        private static StartResult rejected(Failure failure) {
            return new StartResult(null, failure);
        }

        public boolean started() {
            return this.session != null;
        }
    }

    public record StrikeResult(boolean struck, Failure failure, Identifier failedResource, int value, int steps,
                               Map<Identifier, Double> paidCosts) {
        private static StrikeResult struck(int value, int steps, Map<Identifier, Double> paidCosts) {
            return new StrikeResult(true, null, null, value, steps, paidCosts);
        }

        private static StrikeResult rejected(Failure failure, Identifier resource) {
            return new StrikeResult(false, failure, resource, 0, 0, Map.of());
        }
    }

    public record FinishResult(ForgingResultComponent result, Failure failure) {
        private static FinishResult finished(ForgingResultComponent result) {
            return new FinishResult(result, null);
        }

        private static FinishResult rejected(Failure failure) {
            return new FinishResult(null, failure);
        }

        public boolean finished() {
            return this.result != null;
        }
    }
}
