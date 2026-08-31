package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.data.cultivation.CultivateAction;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.CultivationActionService.Failure;
import com.iafenvoy.mxt.runtime.cultivation.CultivationActionService.Result;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.Locale;

/**
 * Owns the player-controlled cultivation mode around a selected cultivation action.
 */
public final class CultivationModeService {
    private CultivationModeService() {
    }

    public static Result toggle(ServerPlayer player) {
        CultivationAttachment spirit = player.getData(MxtAttachments.CULTIVATION);
        Holder<CultivateAction> action = resolveAction(spirit).orElse(null);
        if (action == null)
            return Result.rejected(Failure.NOT_ACTIVE, null);
        if (spirit.cultivating()) return stop(player, spirit, action);

        CultivateAction definition = action.value();
        Result result = CultivationActionService.start(spirit, action, definition,
                player.level().getGameTime(), () -> definition.startCondition().test(player, FormulaContexts.forEntity(player)));
        if (result.started()) {
            CultivationMovementService.reconcile(player);
            player.refreshDimensions();
        }
        return result;
    }

    public static boolean stopIfCultivating(ServerPlayer player) {
        CultivationAttachment spirit = player.getData(MxtAttachments.CULTIVATION);
        Holder<CultivateAction> action = spirit.cultivateAction().orElse(null);
        if (!spirit.cultivating() || action == null) return false;
        stop(player, spirit, action);
        return true;
    }

    /** Sends the user-facing reason for a failed cultivation-mode transition. */
    public static void notifyFailure(ServerPlayer player, Result result) {
        if (result == null || result.failure() == null) return;
        String reasonKey = "actionbar.mxt.cultivation.failure." + result.failure().name().toLowerCase(Locale.ROOT);
        Component reason = result.failure() == Failure.INSUFFICIENT_RESOURCE && result.failedResource() != null
                ? Component.translatable(reasonKey, DefinitionText.name(result.failedResource(), "resource"))
                : Component.translatable(reasonKey);
        player.sendSystemMessage(Component.translatable("actionbar.mxt.cultivation.failed", reason), true);
    }

    /**
     * Resolves the selected cultivation behavior without requiring a cultivation technique.
     */
    public static Optional<Holder<CultivateAction>> resolveAction(CultivationAttachment spirit) {
        Optional<Holder<CultivateAction>> configured = MxtDatapackRegistries.holders(MxtResourceKeys.CULTIVATE_ACTION)
                .filter(action -> action.value().defaultAction())
                .map(action -> (Holder<CultivateAction>) action).findFirst();
        return configured.or(spirit::cultivateAction).or(() -> MxtDatapackRegistries.holders(MxtResourceKeys.CULTIVATE_ACTION)
                .map(action -> (Holder<CultivateAction>) action).findFirst());
    }

    private static Result stop(ServerPlayer player, CultivationAttachment spirit, Holder<CultivateAction> action) {
        Result result = CultivationActionService.stop(player, spirit, HolderHelper.id(action), action.value(), player.level().getGameTime());
        if (result.stopped()) {
            CultivationMovementService.clear(player);
            player.refreshDimensions();
        }
        return result;
    }
}
