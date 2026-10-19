package com.iafenvoy.mxt.runtime.ability;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.failure.Failure;
import com.iafenvoy.mxt.runtime.failure.Failures;
import com.iafenvoy.mxt.util.DefinitionText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * The one table a refusal is read out of, whichever path refused: a press, a cast, a talisman and a cast that failed
 * on its last tick all name the same reason the pipeline told apart, so no caller invents its own wording.
 */
public final class AbilityFeedback {
    private AbilityFeedback() {
    }

    // Both the press and the cast report through this one lookup; running out of one named resource is the only
    // reason that also says which, and that variant is spelled next to the plain key.
    public static Component reason(Failure failure, @Nullable Identifier failedResource) {
        if (failedResource != null && failure == Failures.INSUFFICIENT_RESOURCE)
            return Component.translatable(failure.key() + "_named", DefinitionText.nameOrId(MxtResourceKeys.RESOURCE, failedResource));
        return failure.message();
    }

    // A cast that only fails once its time is up has no press left to answer to, so the actor is told directly.
    public static void reportFailedCast(Entity actor, @Nullable Failure failure,
                                        @Nullable Identifier failedResource) {
        if (failure == null || !(actor instanceof ServerPlayer player)) return;
        MiXianTu.LOGGER.info("Refusing the finished cast for {}: {}{}", player.getGameProfile().name(), failure.name(),
                failedResource == null ? "" : " (" + failedResource + ")");
        player.sendSystemMessage(Component.translatable("actionbar.mxt.wheel.cast_failed",
                reason(failure, failedResource)), true);
    }
}
