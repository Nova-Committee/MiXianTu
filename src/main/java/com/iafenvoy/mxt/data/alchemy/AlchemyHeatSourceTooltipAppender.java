package com.iafenvoy.mxt.data.alchemy;

import com.iafenvoy.mxt.api.AlchemyHeatSource;
import com.iafenvoy.mxt.registry.MxtDataMaps;
import com.iafenvoy.mxt.util.TooltipText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.tooltip.TooltipLocation;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;

import java.util.function.Consumer;

/**
 * What a block heats a furnace to, read from the client's copy of the {@code mxt:heat_source} table. A block
 * implementing {@link AlchemyHeatSource} answers for itself on the server, so its numbers are not on this side:
 * naming the table entry it overrides would state a value the furnace never reads.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class AlchemyHeatSourceTooltipAppender {
    private AlchemyHeatSourceTooltipAppender() {
    }

    @SubscribeEvent
    public static void register(RegisterTooltipAppendersEvent event) {
        event.registerAppender(TooltipLocation.POST_CUSTOM, AlchemyHeatSourceTooltipAppender::append);
    }

    private static void append(ItemStack stack, TooltipContext context, TooltipDisplay display, Player player,
                               TooltipFlag flag, Consumer<Component> builder) {
        if (!(stack.getItem() instanceof BlockItem item)) return;
        Block block = item.getBlock();
        if (block instanceof AlchemyHeatSource) {
            header(builder);
            builder.accept(Component.translatable("tooltip.mxt.alchemy.heat_source_state").withStyle(ChatFormatting.GRAY));
            return;
        }
        HeatSource source = block.defaultBlockState().getData(MxtDataMaps.HEAT_SOURCE);
        if (source == null) return;
        header(builder);
        builder.accept(Component.translatable("tooltip.mxt.alchemy.heat_source_temperature",
                TooltipText.number(source.maxTemperature())).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.mxt.alchemy.heat_source_speed",
                TooltipText.number(source.heatingPerTick())).withStyle(ChatFormatting.GRAY));
    }

    private static void header(Consumer<Component> builder) {
        builder.accept(Component.translatable("tooltip.mxt.alchemy.heat_source").withStyle(ChatFormatting.GOLD));
    }
}
