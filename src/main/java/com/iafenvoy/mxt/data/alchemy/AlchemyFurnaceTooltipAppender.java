package com.iafenvoy.mxt.data.alchemy;

import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationService;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.TooltipText;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.resources.Identifier;
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

import java.util.Optional;
import java.util.function.Consumer;

@EventBusSubscriber(Dist.CLIENT)
public final class AlchemyFurnaceTooltipAppender {
    private AlchemyFurnaceTooltipAppender() {
    }

    @SubscribeEvent
    public static void register(RegisterTooltipAppendersEvent event) {
        event.registerAppender(TooltipLocation.POST_CUSTOM, AlchemyFurnaceTooltipAppender::append);
    }

    private static void append(ItemStack stack, TooltipContext context, TooltipDisplay display, Player player,
                               TooltipFlag flag, Consumer<Component> builder) {
        if (stack.is(MxtBlocks.ALCHEMY_FURNACE_CASING.get().asItem())) {
            Holder<AlchemyWallMaterial> material = loadedWall(context.registries(), stack).orElse(null);
            if (material == null)
                builder.accept(Component.translatable("tooltip.mxt.alchemy.no_wall_material").withStyle(ChatFormatting.RED));
            else {
                builder.accept(DefinitionText.name(material).withStyle(ChatFormatting.DARK_GREEN));
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
        AlchemyWorkstationService.furnaceDefinition(registries, stack).ifPresentOrElse(holder -> {
            AlchemyFurnaceDefinition spec = holder.value();
            builder.accept(DefinitionText.name(holder).withStyle(ChatFormatting.DARK_GREEN));
            if (DefinitionText.resolved(spec.description()) && !spec.description().getString().isBlank())
                builder.accept(spec.description().copy().withStyle(ChatFormatting.GRAY));
            builder.accept(Component.translatable("tooltip.mxt.alchemy.slots", spec.mainSlots(), spec.auxiliarySlots(), spec.catalystSlots())
                    .withStyle(ChatFormatting.GRAY));
            builder.accept(Component.translatable("tooltip.mxt.alchemy.capacity", spec.capacity()).withStyle(ChatFormatting.GRAY));
        }, () -> builder.accept(Component.translatable("screen.mxt.alchemy.no_furnace").withStyle(ChatFormatting.RED)));
        builder.accept(Component.translatable("tooltip.mxt.alchemy.structure").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.mxt.alchemy.fire").withStyle(ChatFormatting.GRAY));
    }

    private static Optional<Holder<AlchemyWallMaterial>> loadedWall(Provider registries, ItemStack stack) {
        if (registries == null) return Optional.empty();
        Holder<AlchemyWallMaterial> stored = stack.get(MxtDataComponents.ALCHEMY_WALL_MATERIAL.get());
        if (stored == null) return Optional.empty();
        Identifier id = HolderHelper.id(stored);
        if (id.equals(HolderHelper.EMPTY)) return Optional.empty();
        return MxtDatapackRegistries.holder(registries, MxtResourceKeys.ALCHEMY_WALL_MATERIAL, id).map(holder -> holder);
    }
}
