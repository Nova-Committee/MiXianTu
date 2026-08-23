package com.iafenvoy.mxt.data.economy;

import com.iafenvoy.mxt.data.CurrencyValue.UnavailableWhen;
import com.iafenvoy.mxt.runtime.economy.CurrencyValueService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
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

/**
 * Adds datapack currency values to item tooltips.
 */
@EventBusSubscriber(Dist.CLIENT)
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
        CurrencyValueService.definition(registries, stack).ifPresent(definition -> {
            boolean valid = definition.unavailableWhen().isEmpty() || player != null && definition.unavailableWhen().stream().noneMatch(entry ->
                    entry.condition().test(player, stack, FormulaContext.EMPTY));
            long value = valid ? definition.value() : 0L;
            builder.accept(Component.translatable("item.mxt.currency_value", value).withStyle(ChatFormatting.GOLD));
            if (!valid) {
                CurrencyValueService.unavailableReason(registries, player, stack)
                        .map(UnavailableWhen::reason)
                        .ifPresentOrElse(reason -> builder.accept(reason.copy().withStyle(ChatFormatting.RED)),
                                () -> builder.accept(Component.translatable("tooltip.mxt.currency_invalid").withStyle(ChatFormatting.RED)));
            }
        });
    }
}
