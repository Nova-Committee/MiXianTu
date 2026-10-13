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
 * Reads a manual out. The technique half of the reading belongs to the technique module, which claims a manual's click
 * before any item runs; a book nothing claims is the only one left to answer here.
 */
public final class TechniqueBookItem extends Item {
    public TechniqueBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) return super.use(level, player, hand);
        ItemStack stack = player.getItemInHand(hand);
        // A binding that claims this book has already printed what it has to say, and the hold it armed is started
        // by the vanilla use; only a book nothing claims is answered here.
        if (ItemBindingService.technique(stack).isPresent()) return super.use(level, player, hand);
        if (ItemReading.readInscription(serverPlayer, stack)) return InteractionResult.SUCCESS_SERVER;
        ItemFeedback.send(player, Component.translatable("item.mxt.technique_book.unbound"));
        return InteractionResult.FAIL;
    }
}
