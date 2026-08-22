package com.iafenvoy.mxt.data.economy;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.runtime.economy.CurrencyValueService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.tooltip.TooltipLocation;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;

import java.util.function.Consumer;

/** Adds datapack currency values to item tooltips. */
@EventBusSubscriber(modid = MiXianTu.MOD_ID, value = Dist.CLIENT)
public final class CurrencyValueTooltipAppender {
    private CurrencyValueTooltipAppender() {
    }

    @SubscribeEvent
    public static void register(RegisterTooltipAppendersEvent event) {
        event.registerAppender(TooltipLocation.POST_CUSTOM, CurrencyValueTooltipAppender::appendCurrencyValue);
    }

    private static void appendCurrencyValue(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                            Player player, TooltipFlag flag, Consumer<Component> builder) {
        Provider registries = context.registries();
        if (registries == null) return;
        CurrencyValueService.unitValue(registries, stack.getItem()).ifPresent(value -> builder.accept(
                Component.translatable("item.mxt.currency_value", value).withStyle(ChatFormatting.GOLD)));
    }
}
