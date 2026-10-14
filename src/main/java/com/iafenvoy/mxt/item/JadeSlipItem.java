package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Reads the text a slip carries back into the chat log.
 */
public final class JadeSlipItem extends Item {
    public JadeSlipItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        // The server-only registry read below is why the client answers with the vanilla use, which is also what
        // starts a hold on a slip a technique binding has claimed.
        if (!(player instanceof ServerPlayer serverPlayer)) return super.use(level, player, hand);
        ItemStack stack = player.getItemInHand(hand);
        // A claimed stack is a manual: the technique module prints it, and its hold is read off the vanilla
        // component, so the vanilla use has to run rather than this class answering the click.
        if (ItemBindingService.technique(level.registryAccess(), stack).isPresent())
            return super.use(level, player, hand);
        if (ItemReading.readInscription(serverPlayer, stack)) return InteractionResult.SUCCESS_SERVER;
        ItemFeedback.send(player, Component.translatable("item.mxt.jade_slip.blank"));
        return InteractionResult.FAIL;
    }
}
