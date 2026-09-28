package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.data.cultivation.Cultivation;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.CultivationMethodService.Failure;
import com.iafenvoy.mxt.runtime.cultivation.CultivationMethodService.Result;
import com.iafenvoy.mxt.runtime.trigger.CultivationTriggerService;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

/**
 * Owns the selected cultivation method: which one a body runs, plus the player-controlled start and stop around it.
 * The pick is "the applicable one of the highest priority", so the attachment records what is running instead of
 * holding a preference that outranks the pack's own ordering.
 */
public final class CultivationModeService {
    private CultivationModeService() {
    }

    public static Result toggle(ServerPlayer player) {
        CultivationAttachment spirit = player.getData(MxtAttachments.CULTIVATION);
        // Stopping goes by the stored method: once the running one no longer applies the selector picks another
        // (or none at all), and stopping by that pick would leave the body cultivating.
        Holder<Cultivation> running = spirit.cultivation().orElse(null);
        if (spirit.cultivating() && running != null) return stop(player, spirit, running);
        FormulaContext context = FormulaContexts.forEntity(player);
        Holder<Cultivation> action = select(player, context).orElse(null);
        if (action == null) return Result.rejected(Failure.NOT_APPLICABLE, null);
        return start(player, spirit, action, context);
    }

    // Applicability is the filter, priority is the order: an equal priority keeps registry order.
    public static Optional<Holder<Cultivation>> select(LivingEntity entity, FormulaContext context) {
        return MxtDatapackRegistries.holders(MxtResourceKeys.CULTIVATION)
                .filter(action -> applicable(entity, action, context))
                .max(Comparator.comparingInt(action -> action.value().priority()))
                .map(action -> (Holder<Cultivation>) action);
    }

    // The one ruler for "usable right now": what start checks, plus the yield condition. The upkeep condition stays
    // out of it - it answers "is the session still on", which cannot hold before one has begun.
    public static boolean applicable(LivingEntity entity, Holder<Cultivation> action, FormulaContext context) {
        Cultivation definition = action.value();
        return definition.startCondition().test(entity, context)
                && definition.cultivateCondition().test(entity, context)
                && CultivationMethodService.canStartCultivation(entity, context);
    }

    public static Result start(LivingEntity entity, CultivationAttachment spirit, Holder<Cultivation> action,
                               FormulaContext context) {
        Cultivation definition = action.value();
        Result result = CultivationMethodService.start(spirit, action, definition, entity.level().getGameTime(),
                () -> applicable(entity, action, context));
        if (result.started()) {
            if (entity instanceof ServerPlayer player) CultivationMovementService.reconcile(player);
            entity.refreshDimensions();
        }
        return result;
    }

    public static Result stop(LivingEntity entity, CultivationAttachment spirit, Holder<Cultivation> action) {
        Result result = CultivationMethodService.stop(entity, spirit, HolderHelper.id(action), action.value(),
                entity.level().getGameTime());
        if (result.stopped()) {
            if (entity instanceof ServerPlayer player) CultivationMovementService.clear(player);
            entity.refreshDimensions();
            CultivationTriggerService.clear(entity);
        }
        return result;
    }

    // The explicit pick behind {@code /mxt cultivate select}: a named method runs now even when the pack's ordering
    // would choose another, but starting still goes through the same ruler, so one that does not apply is refused
    // without disturbing the session already running. Nothing is remembered - the next press of the key picks again.
    public static Result startNamed(LivingEntity entity, Holder<Cultivation> action) {
        CultivationAttachment spirit = entity.getData(MxtAttachments.CULTIVATION);
        Holder<Cultivation> running = spirit.cultivation().orElse(null);
        boolean active = spirit.cultivating() && running != null;
        if (active && HolderHelper.id(running).equals(HolderHelper.id(action)))
            return Result.rejected(Failure.ALREADY_ACTIVE, null);
        FormulaContext context = FormulaContexts.forEntity(entity);
        if (!applicable(entity, action, context)) return Result.rejected(Failure.NOT_APPLICABLE, null);
        if (active) stop(entity, spirit, running);
        return start(entity, spirit, action, context);
    }

    public static boolean stopIfCultivating(ServerPlayer player) {
        CultivationAttachment spirit = player.getData(MxtAttachments.CULTIVATION);
        Holder<Cultivation> action = spirit.cultivation().orElse(null);
        if (!spirit.cultivating() || action == null) return false;
        stop(player, spirit, action);
        return true;
    }

    public static void notifyFailure(ServerPlayer player, Result result) {
        if (result == null || result.failure() == null) return;
        Component reason = result.abortReason();
        if (reason == null) {
            String reasonKey = "actionbar.mxt.cultivation.failure." + result.failure().name().toLowerCase(Locale.ROOT);
            reason = result.failure() == Failure.INSUFFICIENT_RESOURCE && result.failedResource() != null
                    ? Component.translatable(reasonKey, DefinitionText.name(result.failedResource(), "resource"))
                    : Component.translatable(reasonKey);
        }
        player.sendSystemMessage(Component.translatable("actionbar.mxt.cultivation.failed", reason), true);
    }
}
