package com.iafenvoy.mxt.runtime.forging;

import com.iafenvoy.mxt.attachment.ResourceHolderComponent;
import com.iafenvoy.mxt.data.artifact.ForgingResultComponent;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingMethod;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.event.ForgingEvent.Cancel;
import com.iafenvoy.mxt.event.ForgingEvent.CompletePost;
import com.iafenvoy.mxt.event.ForgingEvent.CompletePre;
import com.iafenvoy.mxt.event.ForgingEvent.Start;
import com.iafenvoy.mxt.event.ForgingEvent.Started;
import com.iafenvoy.mxt.event.ForgingEvent.StrikePost;
import com.iafenvoy.mxt.event.ForgingEvent.StrikePre;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Evaluation;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Result;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;

/**
 * Server-side coordinator for method validation, atomic payment and final forging-quality data.
 */
public final class ForgingService {
    private ForgingService() {
    }

    public static StartResult start(ForgingBlueprint blueprint) {
        if (NeoForge.EVENT_BUS.post(new Start(blueprint)).isCanceled())
            return StartResult.rejected(Failure.CANCELLED);
        try {
            StartResult result = StartResult.started(new ForgingSession(blueprint.plan()));
            NeoForge.EVENT_BUS.post(new Started(result.session()));
            return result;
        } catch (IllegalArgumentException exception) {
            return StartResult.rejected(Failure.INVALID_BLUEPRINT);
        }
    }

    public static StrikeResult strike(ForgingSession session, Identifier methodId, ForgingMethod method,
                                      ResourceHolderComponent resources, FormulaContext context, BooleanSupplier conditions) {
        if (!conditions.getAsBoolean()) return StrikeResult.rejected(Failure.CONDITIONS, null);
        if (!session.canStrike(methodId)) return StrikeResult.rejected(Failure.INVALID_STRIKE, null);
        StrikePre event = new StrikePre(session, methodId, method, resources, context);
        if (NeoForge.EVENT_BUS.post(event).isCanceled()) return StrikeResult.rejected(Failure.CANCELLED, null);
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
        NeoForge.EVENT_BUS.post(new StrikePost(session));
        return StrikeResult.struck(session.value(), session.steps(), payment.amounts());
    }

    public static FinishResult finish(Identifier blueprintId, ForgingBlueprint blueprint, ForgingSession session) {
        return finish(blueprintId, session, blueprint::qualityFor);
    }

    public static FinishResult finish(Identifier blueprintId, ForgingSession session, IntFunction<Holder<ItemQuality>> qualityForExtraSteps) {
        if (NeoForge.EVENT_BUS.post(new CompletePre(blueprintId, session)).isCanceled())
            return FinishResult.rejected(Failure.CANCELLED);
        if (!session.canComplete()) return FinishResult.rejected(Failure.NOT_COMPLETE);
        int extra = session.extraSteps();
        Holder<ItemQuality> quality = qualityForExtraSteps.apply(extra);
        if (quality == null) return FinishResult.rejected(Failure.INVALID_BLUEPRINT);
        ForgingResultComponent result = new ForgingResultComponent(blueprintId, session.value(), session.steps(), session.optimalSteps(), extra, quality);
        NeoForge.EVENT_BUS.post(new CompletePost(blueprintId, session, result));
        return FinishResult.finished(result);
    }

    /**
     * Cancels a session before its input is returned by the owning inventory adapter.
     */
    public static boolean cancel(ForgingSession session) {
        return !NeoForge.EVENT_BUS.post(new Cancel(session)).isCanceled();
    }

    public enum Failure {DISABLED, INVALID_BLUEPRINT, CONDITIONS, INVALID_STRIKE, INVALID_FORMULA, INSUFFICIENT_RESOURCE, NOT_COMPLETE, CANCELLED}

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
