package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.runtime.item.IdentificationService;
import com.iafenvoy.saturn.runtime.resulting.Result;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves any stack carrying the generic identification component.
 */
public final class IdentificationMirrorItem extends Item {
    public IdentificationMirrorItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS_SERVER;
        Result<Component> result = IdentificationService.identifyOffHand(serverPlayer);
        Component name = result.getData().orElse(null);
        if (name == null) {
            result.getFailure().ifPresent(failure -> serverPlayer.sendSystemMessage(failure.message(), true));
            return InteractionResult.FAIL;
        }
        serverPlayer.sendSystemMessage(Component.translatable("item.mxt.identification_mirror.identified", name), true);
        return InteractionResult.SUCCESS_SERVER;
    }
}
