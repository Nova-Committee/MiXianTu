package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.registry.MxtDataComponents;

import com.iafenvoy.mxt.data.item.IdentificationComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
        ItemStack mirror = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack target = player.getInventory().getItem(slot);
            IdentificationComponent data = target.get(MxtDataComponents.IDENTIFICATION);
            if (data == null) continue;
            ItemStack resolved = BuiltInRegistries.ITEM.getOptional(data.result()).map(item -> new ItemStack(item, target.getCount())).orElse(ItemStack.EMPTY);
            if (resolved.isEmpty()) continue;
            player.getInventory().setItem(slot, resolved);
            ItemFeedback.send(player, Component.translatable("item.mxt.identification_mirror.identified"));
            return InteractionResult.SUCCESS_SERVER;
        }
        ItemFeedback.send(player, Component.translatable("item.mxt.identification_mirror.none"));
        return InteractionResult.FAIL;
    }
}
