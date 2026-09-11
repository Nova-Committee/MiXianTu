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
 * Explains what a {@code mxt:tool_binding} or {@code mxt:blueprint_binding} actually grants.
 *
 * <p>The two components are what the forge table reads, and on their own they are invisible: a player
 * holds a hammer with no way to know which methods it unlocks, or a manual with no way to know which
 * blueprints it provides, until they have already placed it on a table and looked at the result. This
 * prints that list on the item itself.
 *
 * <p>Both components are datapack holders, so the tooltip is built from the registry the item was
 * decoded against rather than from anything stored on the stack.
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

    /**
     * The heading, which says what kind of list follows rather than whose it is.
     *
     * <p>A binding has no name of its own to print: it is reached through an item, and that item's name is
     * already one line above. What the player cannot see is the category - that these are blueprints, or that
     * these are methods - so that is what goes here.</p>
     */
    private static void header(Consumer<Component> builder, String key) {
        builder.accept(Component.translatable(key).withStyle(ChatFormatting.GOLD));
    }

    /**
     * The registry id under the name it belongs to, and only with advanced tooltips on.
     *
     * <p>It is what a datapack author needs and what a player never does: the name is the icon already drawn
     * on the grid, the id is the string that has to be typed into a file. Indented to the name's own column,
     * so it reads as that entry's id rather than as another entry.</p>
     */
    private static void advancedId(Consumer<Component> builder, TooltipFlag flag, Identifier id) {
        if (!flag.isAdvanced()) return;
        builder.accept(Component.literal("   " + id).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component bullet(Component name) {
        return Component.literal(" - ").append(name).withStyle(ChatFormatting.GRAY);
    }

    /**
     * A method is named by the item it draws itself with, because that icon is what the selector grid
     * shows - and unlike the id it is what the player actually sees. {@link ForgingMethod#displayName}
     * owns that rule so this list and the grid's own tooltip cannot name the same method differently.
     */
    private static Component methodName(Holder<ForgingMethod> method) {
        return method.value().displayName(HolderHelper.id(method));
    }

    /**
     * A blueprint is named by the item it produces, for the same reason: that is the icon on the grid.
     */
    private static Component blueprintName(Holder<ForgingBlueprint> blueprint) {
        return BuiltInRegistries.ITEM.getOptional(blueprint.value().result())
                .map(item -> new ItemStack(item).getHoverName())
                .orElseGet(() -> Component.literal(blueprint.unwrapKey().map(key -> key.identifier().toString()).orElse("?")));
    }
}
