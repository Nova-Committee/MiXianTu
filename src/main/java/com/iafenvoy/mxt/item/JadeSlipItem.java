package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtItems;
import net.minecraft.core.component.DataComponents;
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
 * Reads the text a slip carries back into the chat log. A slip is read by holding it down - the hold itself belongs
 * to the hold module, as every other hold here does - so this item only decides when the read is immediate.
 */
public final class JadeSlipItem extends Item {
    /**
     * What a read takes when the stack says nothing: two seconds.
     */
    public static final int DEFAULT_READ_TIME = 40;

    public JadeSlipItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // The hold module arms the vanilla use component on the click that starts a read; without it there is no
        // gesture to run. Sneaking and a zero length ask for the text outright, which is the item's other behaviour.
        if (player.isShiftKeyDown() || readTime(stack) <= 0 || !stack.has(DataComponents.CONSUMABLE))
            return read(player, stack);
        return super.use(level, player, hand);
    }

    // How long reading this stack takes: its own component, or the standard two seconds.
    public static int readTime(ItemStack stack) {
        return stack.getOrDefault(MxtDataComponents.JADE_SLIP_READING, DEFAULT_READ_TIME);
    }

    public static InteractionResult read(Player player, ItemStack stack) {
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        serverPlayer.sendSystemMessage(stack.getOrDefault(MxtDataComponents.STORED_TEXT,
                Component.translatable("item.mxt.jade_slip.blank")));
        return InteractionResult.SUCCESS_SERVER;
    }

    public static ItemStack create(Component content) {
        ItemStack stack = MxtItems.JADE_SLIP.toStack();
        stack.set(MxtDataComponents.STORED_TEXT, content);
        return stack;
    }
}
