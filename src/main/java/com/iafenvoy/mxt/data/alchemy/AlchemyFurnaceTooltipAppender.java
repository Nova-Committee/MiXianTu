package com.iafenvoy.mxt.data.alchemy;

import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.TooltipText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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

@EventBusSubscriber(Dist.CLIENT)
public final class AlchemyFurnaceTooltipAppender {
    private AlchemyFurnaceTooltipAppender() {
    }

    @SubscribeEvent
    public static void register(RegisterTooltipAppendersEvent event) {
        event.registerAppender(TooltipLocation.POST_CUSTOM, AlchemyFurnaceTooltipAppender::append);
    }

    private static void append(ItemStack stack, TooltipContext context, TooltipDisplay display, Player player, TooltipFlag flag, Consumer<Component> builder) {
        if (stack.is(MxtBlocks.ALCHEMY_FURNACE_CASING.get().asItem())) {
            Holder<AlchemyWallMaterial> material = stack.get(MxtDataComponents.ALCHEMY_WALL_MATERIAL.get());
            if (material == null)
                builder.accept(Component.translatable("tooltip.mxt.alchemy.no_wall_material").withStyle(ChatFormatting.RED));
            else {
                // A heading only a pack can name: an untranslated one is left out rather than drawn as its key.
                DefinitionText.resolved(DefinitionText.text(material))
                        .ifPresent(name -> builder.accept(name.withStyle(ChatFormatting.DARK_GREEN)));
                builder.accept(Component.translatable("tooltip.mxt.alchemy.wall_temperature",
                        TooltipText.number(material.value().maxTemperature())).withStyle(ChatFormatting.GRAY));
            }
            builder.accept(Component.translatable("tooltip.mxt.alchemy.wall_rule").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (stack.is(MxtBlocks.ALCHEMY_MAIN_INPUT.get().asItem())) {
            builder.accept(Component.translatable("tooltip.mxt.alchemy.main_input").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (stack.is(MxtBlocks.ALCHEMY_AUXILIARY_INPUT.get().asItem())) {
            builder.accept(Component.translatable("tooltip.mxt.alchemy.auxiliary_input").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (stack.is(MxtBlocks.ALCHEMY_OUTPUT.get().asItem())) {
            builder.accept(Component.translatable("tooltip.mxt.alchemy.output").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!stack.is(MxtBlocks.ALCHEMY_FURNACE.get().asItem())) return;
        Provider registries = context.registries();
        if (registries == null) return;
        Holder<AlchemyFurnaceDefinition> holder = stack.get(MxtDataComponents.ALCHEMY_FURNACE.get());
        if (holder == null)
            builder.accept(Component.translatable("screen.mxt.alchemy.no_furnace").withStyle(ChatFormatting.RED));
        else {
            AlchemyFurnaceDefinition spec = holder.value();
            // A heading only a pack can name, like the wall material's above: the numbers below stand without it.
            DefinitionText.resolved(DefinitionText.text(holder))
                    .ifPresent(name -> builder.accept(name.withStyle(ChatFormatting.DARK_GREEN)));
            builder.accept(Component.translatable("tooltip.mxt.alchemy.slots", spec.mainSlots(), spec.auxiliarySlots(), spec.catalystSlots())
                    .withStyle(ChatFormatting.GRAY));
            builder.accept(Component.translatable("tooltip.mxt.alchemy.capacity", spec.capacity()).withStyle(ChatFormatting.GRAY));
            spec.maxTemperature().ifPresent(limit -> builder.accept(Component.translatable(
                    "tooltip.mxt.alchemy.furnace_temperature", TooltipText.number(limit)).withStyle(ChatFormatting.GRAY)));
        }
        // The build guide is two long lines; behind Shift they do not push the numbers out of sight.
        if (Minecraft.getInstance().hasShiftDown()) {
            builder.accept(Component.translatable("tooltip.mxt.alchemy.structure").withStyle(ChatFormatting.GRAY));
            builder.accept(Component.translatable("tooltip.mxt.alchemy.heat").withStyle(ChatFormatting.GRAY));
        } else {
            builder.accept(Component.translatable("tooltip.mxt.alchemy.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
