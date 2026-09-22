package com.iafenvoy.mxt.data.forging;

import com.iafenvoy.mxt.registry.MxtDataComponents;
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

import java.util.function.Consumer;

/**
 * Explains what a {@code mxt:tool_binding} or {@code mxt:blueprint_binding} grants: the forge table reads the two
 * components, but on an item they are invisible until it is placed on a table. Both are datapack holders, so the
 * tooltip is built from the registry the item was decoded against.
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
        if (registries == null) return;

        Holder<ToolBinding> tool = stack.get(MxtDataComponents.TOOL_BINDING.get());
        if (tool != null) {
            ToolBinding binding = tool.value();
            header(builder, "tooltip.mxt.forging.tool");
            for (Holder<ForgingMethod> method : binding.methods()) {
                builder.accept(bullet(methodName(method)));
                advancedId(builder, flag, HolderHelper.id(method));
            }
            return;
        }

        Holder<BlueprintBinding> manual = stack.get(MxtDataComponents.BLUEPRINT_BINDING.get());
        if (manual != null) {
            BlueprintBinding binding = manual.value();
            header(builder, "tooltip.mxt.forging.blueprint");
            for (Holder<ForgingBlueprint> blueprint : binding.blueprints()) {
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

    // ForgingMethod#displayName owns that rule, so this list and the selector grid cannot disagree.
    private static Component methodName(Holder<ForgingMethod> method) {
        return method.value().displayName(HolderHelper.id(method));
    }

    private static Component blueprintName(Holder<ForgingBlueprint> blueprint) {
        return BuiltInRegistries.ITEM.getOptional(blueprint.value().result())
                .map(item -> new ItemStack(item).getHoverName())
                .orElseGet(() -> Component.literal(blueprint.unwrapKey().map(key -> key.identifier().toString()).orElse("?")));
    }
}
