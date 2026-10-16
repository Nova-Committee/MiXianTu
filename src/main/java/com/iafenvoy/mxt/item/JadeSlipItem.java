package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtItems;
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
        if (!(player instanceof ServerPlayer serverPlayer)) return super.use(level, player, hand);
        ItemStack stack = player.getItemInHand(hand);
        Component content = stack.getOrDefault(MxtDataComponents.STORED_TEXT, Component.translatable("item.mxt.jade_slip.blank"));
        serverPlayer.sendSystemMessage(content);
        return InteractionResult.SUCCESS_SERVER;
    }

    public static ItemStack create(Component content) {
        ItemStack stack = MxtItems.JADE_SLIP.toStack();
        stack.set(MxtDataComponents.STORED_TEXT, content);
        return stack;
    }
}
