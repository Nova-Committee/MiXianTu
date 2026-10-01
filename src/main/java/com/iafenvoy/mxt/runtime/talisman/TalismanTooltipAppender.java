package com.iafenvoy.mxt.runtime.talisman;

import com.iafenvoy.mxt.data.Talisman;
import com.iafenvoy.mxt.data.aura.SpiritStorageTooltipAppender;
import com.iafenvoy.mxt.item.TalismanBrushItem;
import com.iafenvoy.mxt.item.TalismanItem;
import com.iafenvoy.mxt.util.DefinitionText;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
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

import java.util.List;
import java.util.function.Consumer;

/**
 * Every line a talisman tool shows about itself: what is written on a carrier, how full it is, which of its two
 * gestures a click will get, and which mode it is in. They are written in one appender because the order of the
 * appenders at one location is their registration order, so a line split off into a second one could not promise
 * the reading order a player needs - quality, then the inscriptions, then the charge.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class TalismanTooltipAppender {
    @SubscribeEvent
    public static void register(RegisterTooltipAppendersEvent event) {
        event.registerAppender(TooltipLocation.POST_CUSTOM, TalismanTooltipAppender::appendLines);
    }

    private static void appendLines(ItemStack stack, TooltipContext context, TooltipDisplay display, Player player, TooltipFlag flag, Consumer<Component> builder) {
        // A brush's bar only appears once it holds pigment, so the exact numbers are a tooltip line here rather
        // than a second appender.
        if (stack.getItem() instanceof TalismanBrushItem) {
            builder.accept(Component.translatable("tooltip.mxt.talisman_brush.pigment",
                    BrushPigmentService.pigment(stack), BrushPigmentService.capacity()).withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!(stack.getItem() instanceof TalismanItem)) return;
        List<Holder<Talisman>> inscribed = TalismanService.inscribed(stack);
        if (inscribed.isEmpty()) {
            builder.accept(Component.translatable("tooltip.mxt.talisman.empty"));
        } else {
            for (Holder<Talisman> talisman : inscribed)
                builder.accept(Component.translatable("tooltip.mxt.talisman.entry", DefinitionText.name(talisman, "talisman")));
        }
        if (context.registries() != null)
            SpiritStorageTooltipAppender.chargeLine(context.registries(), stack).ifPresent(builder);
        // A blank carrier says nothing extra: there is no store to fill and nothing to fire.
        if (inscribed.isEmpty()) return;
        String key = TalismanService.ready(stack)
                ? "tooltip.mxt.talisman.ready" : "tooltip.mxt.talisman.charging";
        builder.accept(Component.translatable(key).withStyle(ChatFormatting.DARK_AQUA));
        // The bar only exists once the component does, and a carrier a pack wrote itself carries none until it is
        // first used, so the number is read off the definitions and says "full" until then.
        int durability = TalismanService.durability(stack);
        if (durability > 0)
            builder.accept(Component.translatable("tooltip.mxt.talisman.durability",
                    Math.max(0, durability - stack.getDamageValue()), durability).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.mxt.talisman.mode." + TalismanService.mode(stack).key()));
    }
}
