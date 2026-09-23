package com.iafenvoy.mxt.data.quality;

import com.iafenvoy.mxt.config.MxtClientConfig;
import com.iafenvoy.mxt.data.quality.ItemQuality.Modifier;
import com.iafenvoy.mxt.runtime.item.ItemQualityService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
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
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;
import java.util.function.Consumer;

/**
 * Adds the datapack-resolved quality and its modifiers to item tooltips, and tints the tooltip's name line with
 * the colour the pack gave that tier.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class ItemQualityTooltipAppender {
    private ItemQualityTooltipAppender() {
    }

    @SubscribeEvent
    public static void register(RegisterTooltipAppendersEvent event) {
        event.registerAppender(TooltipLocation.HEAD, ItemQualityTooltipAppender::appendQuality);
    }

    // The appender locations all append, so the name line is the one line they cannot reach: this event is handed
    // the finished list, where line zero is still the item's own display name. Search trees build tooltips through
    // the same call, which is harmless - the list is empty or a name is tinted for a tooltip nobody sees.
    @SubscribeEvent
    public static void recolorName(ItemTooltipEvent event) {
        if (!MxtClientConfig.INSTANCE.tooltips.tintItemName.getValue()) return;
        Provider registries = event.getContext().registries();
        if (registries == null) return;
        List<Component> lines = event.getToolTip();
        if (lines.isEmpty()) return;
        ItemQualityService.find(registries, event.getItemStack())
                .ifPresent(quality -> lines.set(0, ItemQualityService.coloredName(quality, lines.getFirst())));
    }

    private static void appendQuality(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                      Player player, TooltipFlag flag, Consumer<Component> builder) {
        Provider registries = context.registries();
        if (registries == null) return;
        ItemQualityService.find(registries, stack).ifPresent(quality -> appendQuality(builder, quality));
    }

    private static void appendQuality(Consumer<Component> builder, Holder<ItemQuality> quality) {
        ItemQuality value = quality.value();
        builder.accept(Component.translatable("tooltip.mxt.item.quality", ItemQualityService.coloredName(quality, value.name())));
        if (!value.description().getString().isEmpty())
            builder.accept(value.description().copy().withStyle(ChatFormatting.GRAY));
        appendModifier(builder, value.valueMultiplier());
        appendModifier(builder, value.forgingModifier());
        appendModifier(builder, value.alchemyModifier());
    }

    private static void appendModifier(Consumer<Component> builder, Modifier modifier) {
        if (!modifier.description().getString().isEmpty()) builder.accept(modifier.description());
    }
}
