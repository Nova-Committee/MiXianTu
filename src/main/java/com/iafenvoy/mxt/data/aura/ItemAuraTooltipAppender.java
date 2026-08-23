package com.iafenvoy.mxt.data.aura;

import com.iafenvoy.mxt.runtime.cultivation.ItemAuraService;
import com.iafenvoy.mxt.util.TooltipText;
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
 * Adds the datapack-defined aura-fuel information to matching item tooltips.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class ItemAuraTooltipAppender {
    private ItemAuraTooltipAppender() {
    }

    @SubscribeEvent
    public static void register(RegisterTooltipAppendersEvent event) {
        event.registerAppender(TooltipLocation.POST_CUSTOM, ItemAuraTooltipAppender::appendItemAura);
    }

    private static void appendItemAura(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                       Player player, TooltipFlag flag, Consumer<Component> builder) {
        Provider registries = context.registries();
        if (registries == null) return;
        ItemAuraService.find(registries, stack).ifPresent(itemAura -> appendItemAura(builder, itemAura.value()));
    }

    private static void appendItemAura(Consumer<Component> builder, ItemAura itemAura) {
        double total = itemAura.aura().evaluate(FormulaContext.EMPTY);
        double speed = itemAura.consumeSpeed().evaluate(FormulaContext.EMPTY);
        double releaseSpeed = itemAura.releaseSpeed().evaluate(FormulaContext.EMPTY);
        builder.accept(Component.translatable("tooltip.mxt.item.item_aura").withStyle(ChatFormatting.AQUA));
        builder.accept(Component.translatable("tooltip.mxt.item_aura.total", TooltipText.number(total)).withStyle(ChatFormatting.BLUE));
        builder.accept(Component.translatable("tooltip.mxt.item_aura.speed", TooltipText.number(speed)).withStyle(ChatFormatting.BLUE));
        builder.accept(Component.translatable("tooltip.mxt.item_aura.release_speed", TooltipText.number(releaseSpeed)).withStyle(ChatFormatting.BLUE));
        itemAura.resultStack().ifPresent(template -> builder.accept(Component.translatable(
                "tooltip.mxt.item_aura.result", template.create().getHoverName()).withStyle(ChatFormatting.BLUE)));
    }
}
