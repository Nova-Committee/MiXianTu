package com.iafenvoy.mxt.event.handler;

import com.iafenvoy.mxt.data.ChequeData;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.economy.CurrencyValueService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Appends a client-side value line for datapack currencies and signed cheques.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class CurrencyTooltipEvents {
    private CurrencyTooltipEvents() {
    }

    @SubscribeEvent
    public static void appendValue(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        CurrencyValueService.unitValue(event.getContext().registries(), stack.getItem()).ifPresent(value -> event.getToolTip().add(
                Component.translatable("item.mxt.currency_value", value).withStyle(ChatFormatting.GOLD)));
        ChequeData cheque = stack.getOrDefault(MxtDataComponents.CHEQUE.get(), ChequeData.EMPTY);
        if (cheque.value() > 0L) event.getToolTip().add(
                Component.translatable("item.mxt.cheque.value", cheque.value()).withStyle(ChatFormatting.GOLD));
    }
}
