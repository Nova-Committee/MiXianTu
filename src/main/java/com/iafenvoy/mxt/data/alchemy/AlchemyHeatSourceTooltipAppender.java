package com.iafenvoy.mxt.data.alchemy;

import com.iafenvoy.mxt.api.AlchemyHeatSource;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.TooltipText;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.function.Consumer;

/**
 * What a block heats a furnace to, read from the client's copy of the {@code mxt:heat_source} registry. A block
 * implementing {@link AlchemyHeatSource} answers for itself on the server, so its numbers are not on this side:
 * naming the entry it overrides would state a value the furnace never reads.
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
        Provider registries = context.registries();
        if (registries == null) return;
        HeatSource source = find(registries, block);
        if (source == null) return;
        header(builder);
        builder.accept(Component.translatable("tooltip.mxt.alchemy.heat_source_temperature",
                TooltipText.number(source.maxTemperature())).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.mxt.alchemy.heat_source_speed",
                TooltipText.number(source.heatingPerTick())).withStyle(ChatFormatting.GRAY));
    }

    // The highest priority entry claiming this block wins, and a tie keeps the first in registry order - the same
    // reading the server-side index makes, so the line states the number the furnace would actually use.
    private static @Nullable HeatSource find(Provider access, Block block) {
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
        return MxtDatapackRegistries.holders(access, MxtResourceKeys.HEAT_SOURCE)
                .map(Reference::value)
                .filter(definition -> RegistryCodecs.matches(definition.blocks(), BuiltInRegistries.BLOCK, Registries.BLOCK, blockId))
                .max(Comparator.comparingInt(HeatSource::priority))
                .orElse(null);
    }

    private static void header(Consumer<Component> builder) {
        builder.accept(Component.translatable("tooltip.mxt.alchemy.heat_source").withStyle(ChatFormatting.GOLD));
    }
}
