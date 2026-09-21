package com.iafenvoy.mxt.data.artifact;

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
 * Draws {@link ArtifactDescription} on the tooltip of every stack an artifact definition claims, so what a
 * player reads is the same list a probe checks.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class ArtifactTooltipAppender {
    private ArtifactTooltipAppender() {
    }

    @SubscribeEvent
    public static void register(RegisterTooltipAppendersEvent event) {
        event.registerAppender(TooltipLocation.POST_CUSTOM, ArtifactTooltipAppender::appendArtifact);
    }

    private static void appendArtifact(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                       Player player, TooltipFlag flag, Consumer<Component> builder) {
        // No registries means no way to answer what claims the stack; the description reads them and nothing else.
        Provider registries = context.registries();
        if (registries == null) return;
        ArtifactDescription.describe(registries, stack, player, flag.isAdvanced()).forEach(builder);
    }
}
