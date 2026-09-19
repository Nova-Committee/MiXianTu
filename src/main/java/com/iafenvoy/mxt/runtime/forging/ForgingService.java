package com.iafenvoy.mxt.runtime.forging;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.artifact.ForgingResultComponent;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingMethod;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.event.ForgingEvent;
import com.iafenvoy.mxt.event.ForgingEvent.*;
import com.iafenvoy.mxt.runtime.item.ItemQualityService;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Evaluation;
import com.iafenvoy.mxt.runtime.resource.ResourceTransactions.Result;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.HolderHelper;
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
        return finish(player, surface, blueprint, session, qualityForExtraSteps, ItemQualityService.DEFAULT_MODIFIER);
    }

    /**
     * Settles a complete session into its result, reading the extra steps through the forging modifier of
     * the session's own locked materials. The modifier divides the extra count because extra steps are the
     * penalty the smith paid for not finishing optimally: a material of higher grade makes each of those
     * spare strikes count for less, so the piece reaches the better tier of the quality curve it would
     * otherwise have earned. A modifier of exactly one - the codec default, and therefore every existing
     * quality - selects the tier from the session's own count, unchanged. The count stored in
     * {@link ForgingResultComponent} stays the session's own, because that component reports what the
     * smith did rather than what the material was worth.
     */
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

    /**
     * The forging modifier of a settlement's own input: the lowest modifier among the material stacks the
     * session locked, and {@link ItemQualityService#DEFAULT_MODIFIER} when none of them resolves a quality.
     * The lowest, because a piece is only as good as its worst material, and because a batch that mixes one
     * graded ingredient with ungraded ones must not read better than that one ingredient alone.
     *
     * <p>The stacks are the blueprint's declared id and count, which is what the session records when it
     * locks its materials. A quality those items resolve from the datapack - a spirit herb entry, or a
     * binding's quality group default - is therefore visible here, while a quality component that existed
     * only on the particular stack that was consumed is not: the session keeps what it took, not the item
     * it was taken from. Reading the locked materials rather than the tool or blueprint slots is deliberate:
     * those two are never consumed and stay editable during a session, so their quality at settlement would
     * describe the table as it is now instead of the input this piece was forged from.
     */
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

    /**
     * The extra-step count the blueprint's quality curve is read with. A modifier above one earns the piece
     * the better tier of a smith who needed fewer spare strikes; one below one costs it the worse tier. A
     * count that would leave the integer range, or a modifier that is not a usable number at all, leaves
     * the session's own extra steps in place rather than inventing a tier no session produced. The scaled
     * count is rounded to whole steps, because a session counts strikes and the quality curve only knows
     * whole extra steps.
     */
    static int effectiveExtraSteps(int extraSteps, double forgingModifier) {
        if (forgingModifier == ItemQualityService.DEFAULT_MODIFIER) return extraSteps;
        double scaled = extraSteps / forgingModifier;
        if (!Double.isFinite(scaled) || scaled < 0.0D || scaled > Integer.MAX_VALUE) return extraSteps;
        return (int) Math.round(scaled);
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
     * Posts a deciding event. A listener that throws refuses the operation under its own name rather than
     * escaping, because NeoForge rethrows it and every caller sits inside a transaction; a
     * {@link VirtualMachineError} is rethrown.
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
     * Posts a notification: an event whose operation has already been applied, so a listener that throws
     * is logged and the call returns normally rather than undoing the player's own action.
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
         * A listener for the phase threw, so the operation was refused instead of half-performed. Apart from
         * {@link #CANCELLED}: one is a script that said no, the other a script that broke.
         */
        LISTENER_ERROR;

        /**
         * Whether this failure is a listener refusing the request, rather than a verdict on the piece. A
         * session stopped this way is left exactly as it was, unlike one failed with {@link #NOT_COMPLETE}.
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
