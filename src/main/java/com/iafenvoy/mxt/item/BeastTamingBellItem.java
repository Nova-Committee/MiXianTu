package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.creature.ContractService;
import com.iafenvoy.mxt.runtime.creature.ContractService.Result;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Recalls any owned contract creature without requiring a creature-specific command item.
 */
public final class BeastTamingBellItem extends Item {
    public BeastTamingBellItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult interactLivingEntity(@NotNull ItemStack stack, @NotNull Player player,
                                                           @NotNull LivingEntity target, @NotNull InteractionHand hand) {
        if (player.level().isClientSide()) return InteractionResult.SUCCESS;
        Result result = ContractService.setRecalled(target.getData(MxtAttachments.CONTRACT), player.getUUID(), true, false);
        if (!result.changed()) {
            ItemFeedback.send(player, Component.translatable("item.mxt.beast_taming_bell.failed"));
            return InteractionResult.FAIL;
        }
        ItemFeedback.send(player, Component.translatable("item.mxt.beast_taming_bell.recalled"));
        return InteractionResult.SUCCESS;
    }
}
