package com.iafenvoy.mxt.screen.item;

import com.iafenvoy.mxt.item.SpiritStoneBagItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.BundleMouseActions;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(Dist.CLIENT)
public final class SpiritStoneBagMouseActions extends BundleMouseActions {
    private SpiritStoneBagMouseActions(Minecraft minecraft) {
        super(minecraft);
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        // Container initialization clears the mouse actions, including when the screen is resized.
        if (event.getScreen() instanceof AbstractContainerScreen<?> screen)
            screen.addItemSlotMouseAction(new SpiritStoneBagMouseActions(screen.getMinecraft()));
    }

    @Override
    public boolean matches(Slot slot) {
        ItemStack stack = slot.getItem();
        return stack.getItem() instanceof SpiritStoneBagItem && !stack.is(ItemTags.BUNDLES);
    }
}
