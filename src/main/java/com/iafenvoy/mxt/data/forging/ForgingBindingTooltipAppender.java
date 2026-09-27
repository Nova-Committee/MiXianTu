package com.iafenvoy.mxt.data.forging;

import com.iafenvoy.mxt.runtime.forging.ForgingBindingService;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
 * Explains what a tool or a sheet offers: the forge table reads it the same way, but on an item it is invisible
 * until it is placed on a table. The answer is a declaration claiming the stack plus whatever the stack carries
 * itself, so it is the same one {@link ForgingBindingService} gives the table.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class ForgingBindingTooltipAppender {
    private ForgingBindingTooltipAppender() {
    }

    @SubscribeEvent
    public static void register(RegisterTooltipAppendersEvent event) {
        event.registerAppender(TooltipLocation.POST_CUSTOM, ForgingBindingTooltipAppender::appendBinding);
    }

    private static void appendBinding(ItemStack stack, TooltipContext context, TooltipDisplay display, Player player, TooltipFlag flag, Consumer<Component> builder) {
        Provider registries = context.registries();
        if (registries == null || stack.isEmpty()) return;

        List<Holder<ForgingMethod>> methods = ForgingBindingService.methods(registries, stack);
        if (!methods.isEmpty()) {
            header(builder, "tooltip.mxt.forging.tool");
            for (Holder<ForgingMethod> method : methods) {
                builder.accept(bullet(method.value().displayName(HolderHelper.id(method))));
                advancedId(builder, flag, HolderHelper.id(method));
            }
            return;
        }

        List<Holder<ForgingBlueprint>> blueprints = ForgingBindingService.blueprints(registries, stack);
        if (!blueprints.isEmpty()) {
            header(builder, "tooltip.mxt.forging.blueprint");
            for (Holder<ForgingBlueprint> blueprint : blueprints) {
                builder.accept(bullet(blueprintName(blueprint)));
                advancedId(builder, flag, HolderHelper.id(blueprint));
            }
            builder.accept(Component.translatable("tooltip.mxt.forging.place").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void header(Consumer<Component> builder, String key) {
        builder.accept(Component.translatable(key).withStyle(ChatFormatting.GOLD));
    }

    private static void advancedId(Consumer<Component> builder, TooltipFlag flag, Identifier id) {
        if (!flag.isAdvanced()) return;
        builder.accept(Component.literal("   " + id).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component bullet(Component name) {
        return Component.literal(" - ").append(name).withStyle(ChatFormatting.GRAY);
    }

    private static Component blueprintName(Holder<ForgingBlueprint> blueprint) {
        return BuiltInRegistries.ITEM.getOptional(blueprint.value().result())
                .map(item -> new ItemStack(item).getHoverName())
                .orElseGet(() -> Component.literal(blueprint.unwrapKey().map(key -> key.identifier().toString()).orElse("?")));
    }
}
