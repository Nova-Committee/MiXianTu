package com.iafenvoy.mxt.runtime.item;

import com.iafenvoy.mxt.data.item.IdentificationComponent;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.saturn.runtime.resulting.Failures;
import com.iafenvoy.saturn.runtime.resulting.Result;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class IdentificationService {
    private IdentificationService() {
    }

    public static Result<Component> identifyOffHand(Player player) {
        if (player.level().isClientSide()) return Result.failure(Failures.SERVER_ONLY);
        ItemStack input = player.getOffhandItem();
        IdentificationComponent data = input.get(MxtDataComponents.IDENTIFICATION);
        if (input.isEmpty() || data == null) return Result.failure(Refusal.NO_TARGET);

        Item item = BuiltInRegistries.ITEM.getOptional(data.result()).orElse(null);
        if (item == null || item == MxtItems.UNIDENTIFIED_ITEM.get()) return Result.failure(Refusal.INVALID_RESULT);
        ItemStack output = item.getDefaultInstance();
        output.setCount(1);
        output.applyComponents(data.resultComponents());
        if (output.isEmpty() || !output.isItemEnabled(player.level().enabledFeatures())
                || output.has(MxtDataComponents.IDENTIFICATION) || ItemStack.validateStrict(output).isError())
            return Result.failure(Refusal.INVALID_RESULT);

        Inventory inventory = player.getInventory();
        if (input.getCount() == 1) {
            player.setItemInHand(InteractionHand.OFF_HAND, output);
        } else {
            // Creative insertion can consume an item when the inventory has no room.
            if ((inventory.getSlotWithRemainingSpace(output) < 0 && inventory.getFreeSlot() < 0)
                    || !inventory.add(output.copy()))
                return Result.failure(Refusal.INVENTORY_FULL);
            input.shrink(1);
        }
        inventory.setChanged();
        return Result.success(output.getHoverName());
    }

    private enum Refusal implements Failure {
        NO_TARGET("none"),
        INVALID_RESULT("invalid_result"),
        INVENTORY_FULL("inventory_full");

        private final String translationKey;

        Refusal(String suffix) {
            this.translationKey = "item.mxt.identification_mirror." + suffix;
        }

        @Override
        public String key() {
            return this.translationKey;
        }
    }
}
