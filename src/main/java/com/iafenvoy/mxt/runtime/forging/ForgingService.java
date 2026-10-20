package com.iafenvoy.mxt.runtime.forging;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.artifact.ForgingResultComponent;
import com.iafenvoy.mxt.data.cost.CostPayment;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostOrigin;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingMethod;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.event.ForgingEvent;
import com.iafenvoy.mxt.event.ForgingEvent.*;
import com.iafenvoy.mxt.runtime.item.QualityService;
import com.iafenvoy.mxt.runtime.resulting.Failure;
import com.iafenvoy.mxt.runtime.resulting.Failures;
import com.iafenvoy.mxt.runtime.resulting.Result;
import com.iafenvoy.mxt.runtime.resulting.ResultWithData;
import com.iafenvoy.mxt.runtime.resulting.failures.ForgingFailure;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.profiling.ProfilerScope;
import com.iafenvoy.mxt.util.profiling.ServerProfiling;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;

/**
 * Server-side coordinator for method validation, atomic payment and final forging-quality data.
 */
public final class ForgingService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ForgingService() {
    }

    public static StartResult start(ServerPlayer player, ForgingSurface surface, ForgingBlueprint blueprint, RegistryAccess registries) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.FORGING)) {
            Failure refusal = postEvent(new Start(player, surface.pos(), blueprint));
            if (refusal != null) return StartResult.rejected(refusal);
            ForgingPlan plan;
            try {
                plan = ForgingPlan.of(blueprint, registries);
            } catch (IllegalArgumentException exception) {
                return StartResult.rejected(ForgingFailure.INVALID_BLUEPRINT);
            }
            // After the try, and deliberately so: a `Started` listener throwing an IllegalArgumentException
            // would land in that catch and be reported as a broken blueprint.
            ForgingSession session = new ForgingSession(plan);
            notifyListeners(new Started(player, surface.pos(), new ForgingSessionView(session)));
            return StartResult.started(session);
        }
    }

    public static StrikeResult strike(ServerPlayer player, ForgingSurface surface, ForgingSession session, Holder<ForgingMethod> method,
                                      ResourceHolderAttachment resources, FormulaContext context, BooleanSupplier conditions) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.FORGING)) {
            if (!conditions.getAsBoolean()) return StrikeResult.rejected(Failures.CONDITIONS, null);
            if (!session.canStrike(method)) return StrikeResult.rejected(ForgingFailure.INVALID_STRIKE, null);
            StrikePre event = new StrikePre(player, surface.pos(), new ForgingSessionView(session), method, resources, context);
            Failure refusal = postEvent(event);
            if (refusal != null) return StrikeResult.rejected(refusal, null);
            CostContext costContext = CostContext.of(player, context, CostOrigin.FORGING);
            CostPayment plan = CostPayment.of(costContext);
            if (plan.price(event.getCosts()).isPresent())
                return StrikeResult.rejected(Failures.INVALID_FORMULA, null);
            CostPayment.PaymentResult payment = plan.pay();
            if (!payment.isSuccess())
                return StrikeResult.rejected(Failures.INSUFFICIENT_RESOURCE, payment.failedResource());
            if (!session.strike(method))
                throw new IllegalStateException("Forging session changed after its strike precheck");
            notifyListeners(new StrikePost(player, surface.pos(), new ForgingSessionView(session)));
            return StrikeResult.struck(session.value(), session.steps(), payment.resources());
        }
    }

    public static FinishResult finish(ServerPlayer player, ForgingSurface surface, Holder<ForgingBlueprint> blueprint, ForgingSession session) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.FORGING)) {
            return finish(player, surface, blueprint, session, blueprint.value()::qualityFor);
        }
    }

    public static FinishResult finish(ServerPlayer player, ForgingSurface surface, Holder<ForgingBlueprint> blueprint, ForgingSession session,
                                      IntFunction<Holder<ItemQuality>> qualityForExtraSteps) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.FORGING)) {
            return finish(player, surface, blueprint, session, qualityForExtraSteps, QualityService.DEFAULT_MODIFIER);
        }
    }

    // The modifier divides the extra count because extra steps are the penalty for not finishing optimally: a
    // higher-grade material makes each spare strike count for less, reaching the better tier of the curve. The
    // count stored in ForgingResultComponent stays the session's own, because it reports what the smith did.
    static FinishResult finish(ServerPlayer player, ForgingSurface surface, Holder<ForgingBlueprint> blueprint, ForgingSession session,
                               IntFunction<Holder<ItemQuality>> qualityForExtraSteps, double forgingModifier) {
        Failure refusal = postEvent(new CompletePre(player, surface.pos(), blueprint, new ForgingSessionView(session)));
        if (refusal != null) return FinishResult.rejected(refusal);
        if (!session.canComplete()) return FinishResult.rejected(ForgingFailure.NOT_COMPLETE);
        int extra = session.extraSteps();
        Holder<ItemQuality> quality = qualityForExtraSteps.apply(effectiveExtraSteps(extra, forgingModifier));
        if (quality == null) return FinishResult.rejected(ForgingFailure.INVALID_BLUEPRINT);
        ForgingResultComponent result = new ForgingResultComponent(blueprint, session.value(), session.steps(), session.optimalSteps(), extra);
        notifyListeners(new CompletePost(player, surface.pos(), blueprint, new ForgingSessionView(session), result, quality));
        return FinishResult.finished(result, quality);
    }

    // The lowest modifier among the materials the session locked, because a piece is only as good as its
    // worst material. Deliberately the locked stacks and not the tool or blueprint slots: those two are never
    // consumed and stay editable during a session, so their quality at settlement would describe the table as
    // it is now rather than the input this piece was forged from.
    static double materialModifier(RegistryAccess access, List<ItemStack> consumed, FormulaContext context) {
        double modifier = QualityService.DEFAULT_MODIFIER;
        boolean graded = false;
        for (ItemStack stack : consumed) {
            Optional<Holder<ItemQuality>> quality = QualityService.find(access, stack);
            if (quality.isEmpty()) continue;
            double value = QualityService.modifier(quality.orElseThrow(), ItemQuality::forgingModifier, context);
            modifier = graded ? Math.min(modifier, value) : value;
            graded = true;
        }
        return modifier;
    }

    // A count that would leave the integer range, or a modifier that is not a usable number, leaves the
    // session's own extra steps in place rather than inventing a tier no session produced.
    static int effectiveExtraSteps(int extraSteps, double forgingModifier) {
        if (forgingModifier == QualityService.DEFAULT_MODIFIER) return extraSteps;
        double scaled = extraSteps / forgingModifier;
        if (!Double.isFinite(scaled) || scaled < 0.0D || scaled > Integer.MAX_VALUE) return extraSteps;
        return (int) Math.round(scaled);
    }

    // Cancels a session before its input is returned by the owning inventory adapter. Returns the refusal to
    // report, or null when the cancellation stands.
    public static @Nullable Failure cancel(ServerPlayer player, ForgingSurface surface, ForgingSession session) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.FORGING)) {
            return postEvent(new Cancel(player, surface.pos(), new ForgingSessionView(session)));
        }
    }

    // A listener that throws refuses the operation under its own name rather than escaping, because NeoForge
    // rethrows it and every caller sits inside a transaction.
    static <T extends ForgingEvent & ICancellableEvent> @Nullable Failure postEvent(T event) {
        try {
            return NeoForge.EVENT_BUS.post(event).isCanceled() ? Failures.CANCELLED : null;
        } catch (VirtualMachineError error) {
            throw error;
        } catch (Throwable throwable) {
            LOGGER.error("A {} listener threw; the forging operation is refused as {}",
                    event.getClass().getSimpleName(), ForgingFailure.LISTENER_ERROR, throwable);
            return ForgingFailure.LISTENER_ERROR;
        }
    }

    // The operation has already been applied, so a listener that throws is logged and the call returns
    // normally rather than undoing the player's own action.
    static void notifyListeners(ForgingEvent event) {
        try {
            NeoForge.EVENT_BUS.post(event);
        } catch (VirtualMachineError error) {
            throw error;
        } catch (Throwable throwable) {
            LOGGER.error("A {} listener threw; the forging operation had already been applied and stands",
                    event.getClass().getSimpleName(), throwable);
        }
    }

    public static final class StartResult extends ResultWithData<ForgingSession> {
        private StartResult(@Nullable ForgingSession session, Failure failure) {
            super(session, failure, null);
        }

        private static StartResult started(ForgingSession session) {
            return new StartResult(session, null);
        }

        private static StartResult rejected(Failure failure) {
            return new StartResult(null, failure);
        }
    }

    public static final class StrikeResult extends Result {
        private final int value;
        private final int steps;
        private final Map<Identifier, Double> paidCosts;

        private StrikeResult(@Nullable Failure failure, @Nullable Identifier failedResource, int value, int steps,
                             Map<Identifier, Double> paidCosts) {
            super(failure, failedResource);
            this.value = value;
            this.steps = steps;
            this.paidCosts = paidCosts;
        }

        private static StrikeResult struck(int value, int steps, Map<Identifier, Double> paidCosts) {
            return new StrikeResult(null, null, value, steps, paidCosts);
        }

        private static StrikeResult rejected(Failure failure, Identifier resource) {
            return new StrikeResult(failure, resource, 0, 0, Map.of());
        }

        public int value() {
            return this.value;
        }

        public int steps() {
            return this.steps;
        }

        public Map<Identifier, Double> paidCosts() {
            return this.paidCosts;
        }
    }

    // The tier is carried beside the record rather than inside it: the workstation writes it to the one quality
    // component, and the record keeps only what the smith did.
    public static final class FinishResult extends Result {
        private final @Nullable ForgingResultComponent result;
        private final @Nullable Holder<ItemQuality> quality;

        private FinishResult(@Nullable ForgingResultComponent result, @Nullable Holder<ItemQuality> quality,
                             Failure failure) {
            super(failure, null);
            this.result = result;
            this.quality = quality;
        }

        private static FinishResult finished(ForgingResultComponent result, Holder<ItemQuality> quality) {
            return new FinishResult(result, quality, null);
        }

        private static FinishResult rejected(Failure failure) {
            return new FinishResult(null, null, failure);
        }

        public @Nullable ForgingResultComponent result() {
            return this.result;
        }

        public @Nullable Holder<ItemQuality> quality() {
            return this.quality;
        }
    }
}
