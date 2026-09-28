package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.registry.MxtItems;
import net.minecraft.ChatFormatting;
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
 * The one line a fried dough cake has to say.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class FriedDoughCakeTooltipAppender {
    private FriedDoughCakeTooltipAppender() {
    }

    @SubscribeEvent
    public static void register(RegisterTooltipAppendersEvent event) {
        event.registerAppender(TooltipLocation.POST_CUSTOM, FriedDoughCakeTooltipAppender::appendHint);
    }

    private static void appendHint(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                   Player player, TooltipFlag flag, Consumer<Component> builder) {
        if (!stack.is(MxtItems.FRIED_DOUGH_CAKE.get())) return;
        builder.accept(Component.translatable("tooltip.mxt.fried_dough_cake").withStyle(ChatFormatting.GOLD));
    }
}
