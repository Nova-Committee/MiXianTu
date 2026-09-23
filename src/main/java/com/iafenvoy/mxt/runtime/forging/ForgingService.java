package com.iafenvoy.mxt.runtime.forging;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.artifact.ForgingResultComponent;
import com.iafenvoy.mxt.data.cost.CostTransaction;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostOrigin;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingMethod;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.event.ForgingEvent;
import com.iafenvoy.mxt.event.ForgingEvent.*;
import com.iafenvoy.mxt.runtime.item.ItemQualityService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        // After the try, and deliberately so: a `Started` listener throwing an IllegalArgumentException
        // would land in that catch and be reported as a broken blueprint.
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
        CostContext costContext = CostContext.of(player, context, CostOrigin.FORGING);
        CostTransaction.Planning plan = CostTransaction.plan(event.costs(), costContext, resources, null);
        if (!plan.ok()) return StrikeResult.rejected(Failure.INVALID_FORMULA, null);
        CostTransaction.PayResult payment = CostTransaction.commit(plan, costContext, resources);
        if (!payment.paid()) return StrikeResult.rejected(Failure.INSUFFICIENT_RESOURCE, payment.failedResource());
        if (!session.strike(methodId))
            throw new IllegalStateException("Forging session changed after its strike precheck");
        notifyListeners(new StrikePost(player, surface.pos(), new ForgingSessionView(session)));
        return StrikeResult.struck(session.value(), session.steps(), payment.resources());
    }

    public static FinishResult finish(ServerPlayer player, ForgingSurface surface, Holder<ForgingBlueprint> blueprint, ForgingSession session) {
        return finish(player, surface, blueprint, session, blueprint.value()::qualityFor);
    }

    public static FinishResult finish(ServerPlayer player, ForgingSurface surface, Holder<ForgingBlueprint> blueprint, ForgingSession session,
                                      IntFunction<Holder<ItemQuality>> qualityForExtraSteps) {
        return finish(player, surface, blueprint, session, qualityForExtraSteps, ItemQualityService.DEFAULT_MODIFIER);
    }

    // The modifier divides the extra count because extra steps are the penalty for not finishing optimally: a
    // higher-grade material makes each spare strike count for less, reaching the better tier of the curve. The
    // count stored in ForgingResultComponent stays the session's own, because it reports what the smith did.
    static FinishResult finish(ServerPlayer player, ForgingSurface surface, Holder<ForgingBlueprint> blueprint, ForgingSession session,
                               IntFunction<Holder<ItemQuality>> qualityForExtraSteps, double forgingModifier) {
        Failure refusal = postEvent(new CompletePre(player, surface.pos(), blueprint, new ForgingSessionView(session)));
        if (refusal != null) return FinishResult.rejected(refusal);
        if (!session.canComplete()) return FinishResult.rejected(Failure.NOT_COMPLETE);
        int extra = session.extraSteps();
        Holder<ItemQuality> quality = qualityForExtraSteps.apply(effectiveExtraSteps(extra, forgingModifier));
        if (quality == null) return FinishResult.rejected(Failure.INVALID_BLUEPRINT);
        ForgingResultComponent result = new ForgingResultComponent(HolderHelper.id(blueprint), session.value(), session.steps(), session.optimalSteps(), extra, quality);
        notifyListeners(new CompletePost(player, surface.pos(), blueprint, new ForgingSessionView(session), result));
        return FinishResult.finished(result);
    }

    // The lowest modifier among the materials the session locked, because a piece is only as good as its
    // worst material. Deliberately the locked stacks and not the tool or blueprint slots: those two are never
    // consumed and stay editable during a session, so their quality at settlement would describe the table as
    // it is now rather than the input this piece was forged from.
    static double materialModifier(RegistryAccess access, List<ItemStack> consumed, FormulaContext context) {
        double modifier = ItemQualityService.DEFAULT_MODIFIER;
        boolean graded = false;
        for (ItemStack stack : consumed) {
            Optional<Holder<ItemQuality>> quality = ItemQualityService.find(access, stack);
            if (quality.isEmpty()) continue;
            double value = ItemQualityService.modifier(quality.orElseThrow(), ItemQuality::forgingModifier, context);
            modifier = graded ? Math.min(modifier, value) : value;
            graded = true;
        }
        return modifier;
    }

    // A count that would leave the integer range, or a modifier that is not a usable number, leaves the
    // session's own extra steps in place rather than inventing a tier no session produced.
    static int effectiveExtraSteps(int extraSteps, double forgingModifier) {
        if (forgingModifier == ItemQualityService.DEFAULT_MODIFIER) return extraSteps;
        double scaled = extraSteps / forgingModifier;
        if (!Double.isFinite(scaled) || scaled < 0.0D || scaled > Integer.MAX_VALUE) return extraSteps;
        return (int) Math.round(scaled);
    }

    // Cancels a session before its input is returned by the owning inventory adapter. Returns the refusal to
    // report, or null when the cancellation stands.
    public static @Nullable Failure cancel(ServerPlayer player, ForgingSurface surface, ForgingSession session) {
        return postEvent(new Cancel(player, surface.pos(), new ForgingSessionView(session)));
    }

    // A listener that throws refuses the operation under its own name rather than escaping, because NeoForge
    // rethrows it and every caller sits inside a transaction.
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

    // The operation has already been applied, so a listener that throws is logged and the call returns
    // normally rather than undoing the player's own action.
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
        NO_SESSION,
        ALREADY_ACTIVE,
        OUT_OF_RANGE,
        BLUEPRINT_NOT_HELD,
        // The struck method is not in the intersection of the blueprint's allowed methods and the methods the
        // placed tools unlock.
        METHOD_NOT_AVAILABLE,
        INSUFFICIENT_MATERIALS,
        OUTPUT_BLOCKED,
        COOLDOWN,
        // A listener for the phase threw, so the operation was refused instead of half-performed. Apart from
        // CANCELLED: one is a script that said no, the other a script that broke.
        LISTENER_ERROR;

        // A session stopped this way is left exactly as it was, unlike one failed with NOT_COMPLETE.
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
