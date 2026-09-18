package com.iafenvoy.mxt.runtime.talisman;

import com.iafenvoy.mxt.item.TalismanItem;
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
 * Tells a player which of a carrier's two gestures a click will get, because nothing else does: the charge bar
 * shows how far along it is, not what to do about it, and a carrier that has never been poured into looks
 * exactly like one that is merely empty.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class TalismanTooltipAppender {
    @SubscribeEvent
    public static void register(RegisterTooltipAppendersEvent event) {
        event.registerAppender(TooltipLocation.POST_CUSTOM, TalismanTooltipAppender::appendHint);
    }

    private static void appendHint(ItemStack stack, TooltipContext context, TooltipDisplay display, Player player, TooltipFlag flag, Consumer<Component> builder) {
        if (!(stack.getItem() instanceof TalismanItem)) return;
        // A blank carrier says nothing extra: there is no bill to fill and nothing to fire.
        if (TalismanService.inscribed(stack).isEmpty()) return;
        Provider registries = context.registries();
        if (registries == null) return;
        String key = TalismanService.ready(registries, stack)
                ? "tooltip.mxt.talisman.ready" : "tooltip.mxt.talisman.charging";
        builder.accept(Component.translatable(key).withStyle(ChatFormatting.DARK_AQUA));
    }
}
