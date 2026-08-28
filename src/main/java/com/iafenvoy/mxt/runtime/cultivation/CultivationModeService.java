package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.SpiritAttachment;
import com.iafenvoy.mxt.data.cultivation.CultivateAction;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.CultivationActionService.Failure;
import com.iafenvoy.mxt.runtime.cultivation.CultivationActionService.Result;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Owns the player-controlled cultivation mode around a selected cultivation action.
 */
public final class CultivationModeService {
    private CultivationModeService() {
    }

    public static Result toggle(ServerPlayer player) {
        SpiritAttachment spirit = player.getData(MxtAttachments.SPIRIT_DATA);
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
        SpiritAttachment spirit = player.getData(MxtAttachments.SPIRIT_DATA);
        Holder<CultivateAction> action = spirit.cultivateAction().orElse(null);
        if (!spirit.cultivating() || action == null) return false;
        stop(player, spirit, action);
        return true;
    }

    /**
     * Resolves the selected cultivation behavior without requiring a cultivation technique.
     */
    public static Optional<Holder<CultivateAction>> resolveAction(SpiritAttachment spirit) {
        Optional<Holder<CultivateAction>> configured = MxtDatapackRegistries.holders(MxtResourceKeys.CULTIVATE_ACTION)
                .filter(action -> action.value().defaultAction())
                .map(action -> (Holder<CultivateAction>) action).findFirst();
        return configured.or(spirit::cultivateAction).or(() -> MxtDatapackRegistries.holders(MxtResourceKeys.CULTIVATE_ACTION)
                .map(action -> (Holder<CultivateAction>) action).findFirst());
    }

    private static Result stop(ServerPlayer player, SpiritAttachment spirit, Holder<CultivateAction> action) {
        Result result = CultivationActionService.stop(player, spirit, HolderHelper.id(action), action.value(), player.level().getGameTime());
        if (result.stopped()) {
            CultivationMovementService.clear(player);
            player.refreshDimensions();
        }
        return result;
    }
}
