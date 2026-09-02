package com.iafenvoy.mxt.data.quality;

import com.iafenvoy.mxt.data.quality.ItemQuality.Modifier;
import com.iafenvoy.mxt.runtime.item.ItemQualityService;
import com.iafenvoy.mxt.util.DefinitionText;
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

import java.util.function.Consumer;

/**
 * Adds the datapack-resolved quality and its modifiers to item tooltips.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class ItemQualityTooltipAppender {
    private ItemQualityTooltipAppender() {
    }

    @SubscribeEvent
    public static void register(RegisterTooltipAppendersEvent event) {
        event.registerAppender(TooltipLocation.POST_CUSTOM, ItemQualityTooltipAppender::appendQuality);
    }

    private static void appendQuality(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                      Player player, TooltipFlag flag, Consumer<Component> builder) {
        Provider registries = context.registries();
        if (registries == null) return;
        ItemQualityService.find(registries, stack).ifPresent(quality -> appendQuality(builder, flag, quality));
    }

    private static void appendQuality(Consumer<Component> builder, TooltipFlag flag, Holder<ItemQuality> quality) {
        ItemQuality value = quality.value();
        builder.accept(Component.translatable("tooltip.mxt.item.quality", value.displayName()));
        appendModifier(builder, value.valueMultiplier());
        appendModifier(builder, value.forgingModifier());
        appendModifier(builder, value.alchemyModifier());
        if (flag.isAdvanced())
            builder.accept(DefinitionText.name(quality, "quality").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void appendModifier(Consumer<Component> builder, Modifier modifier) {
        if (!modifier.description().getString().isEmpty()) builder.accept(modifier.description());
    }
}
