package com.iafenvoy.mxt.runtime.wheel;

import com.iafenvoy.mxt.data.creature.ContractBehavior;
import com.iafenvoy.mxt.data.creature.ContractBehaviors;
import com.iafenvoy.mxt.runtime.creature.ContractBehaviorService;
import com.iafenvoy.mxt.runtime.creature.ContractBells;
import com.iafenvoy.mxt.runtime.resulting.failures.ContractFailure;
import com.iafenvoy.mxt.runtime.spirit.SpiritBurstService;
import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.saturn.runtime.resulting.Result;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

public final class MxtWheelActions {
    private MxtWheelActions() {
    }

    public static boolean burst(ServerPlayer player, Identifier id) {
        Result<Void> result = SpiritBurstService.fireOnceResult(player, id);
        if (result instanceof Result.FailureResult<Void>(Failure failure)) {
            player.sendSystemMessage(Component.translatable("actionbar.mxt.wheel.burst_failed", failure.message()), true);
            return false;
        }
        return true;
    }

    public static boolean order(ServerPlayer player, Identifier id) {
        ContractBehavior behavior = ContractBehaviors.byId(id).orElse(null);
        if (behavior == null) {
            player.sendSystemMessage(ContractFailure.UNSUPPORTED_BEHAVIOR.message(), true);
            return false;
        }
        Mob beast = ContractBells.beast(player).orElse(null);
        if (beast == null) {
            player.sendSystemMessage(Component.translatable("actionbar.mxt.contract.no_beast"), true);
            return false;
        }
        Result<?> result = ContractBehaviorService.request(beast, player.getUUID(), behavior, false);
        if (result instanceof Result.FailureResult<?>(Failure failure1)) {
            player.sendSystemMessage(failure1.message(), true);
            return false;
        }
        player.sendSystemMessage(Component.translatable("actionbar.mxt.contract.ordered", beast.getDisplayName(), behavior.name()), true);
        return true;
    }
}
