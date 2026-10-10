package com.iafenvoy.mxt.runtime.talisman;

import com.iafenvoy.mxt.data.Talisman;
import com.iafenvoy.mxt.data.aura.SpiritStorageTooltipAppender;
import com.iafenvoy.mxt.data.talisman.TalismanType;
import com.iafenvoy.mxt.item.TalismanBrushItem;
import com.iafenvoy.mxt.item.TalismanItem;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Every line a talisman tool shows about itself: what is written on a carrier, how each use type works, how full it
 * is, which of its two gestures a click will get, and which mode it is in. They are written in one appender because
 * the order of the appenders at one location is their registration order, so a line split off into a second one could
 * not promise the reading order a player needs - quality, then the inscriptions, then the charge.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class TalismanTooltipAppender {
    // A use type's own line, keyed `talisman_type.mxt.mxt.<type path>` - the four-part shape every definition text
    // uses, with the type registry's namespace in the middle. One line per distinct type on the carrier, so several
    // inscriptions of the same kind are explained once.
    private static final String USAGE_CATEGORY = DefinitionText.category(MxtResourceKeys.TALISMAN_TYPE.identifier());
    private static final String TYPE_REGISTRY_NAMESPACE = MxtResourceKeys.TALISMAN_TYPE.identifier().getNamespace();

    @SubscribeEvent
    public static void register(RegisterTooltipAppendersEvent event) {
        event.registerAppender(TooltipLocation.POST_CUSTOM, TalismanTooltipAppender::appendLines);
    }

    private static void appendLines(ItemStack stack, TooltipContext context, TooltipDisplay display, Player player, TooltipFlag flag, Consumer<Component> builder) {
        // A brush's bar only appears once it holds pigment, so the exact numbers are a tooltip line here rather
        // than a second appender.
        if (stack.getItem() instanceof TalismanBrushItem) {
            builder.accept(Component.translatable("tooltip.mxt.talisman_brush.pigment",
                    BrushPigmentService.pigment(stack), BrushPigmentService.capacity()).withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!(stack.getItem() instanceof TalismanItem)) return;
        List<Holder<Talisman>> inscribed = TalismanService.inscribed(stack);
        if (inscribed.isEmpty()) {
            builder.accept(Component.translatable("tooltip.mxt.talisman.empty"));
        } else {
            Set<Identifier> explained = new HashSet<>();
            for (Holder<Talisman> talisman : inscribed) {
                builder.accept(Component.translatable("tooltip.mxt.talisman.entry", DefinitionText.name(talisman, "talisman")));
                usageLine(talisman.value().type(), explained).ifPresent(builder);
            }
        }
        if (context.registries() != null)
            SpiritStorageTooltipAppender.chargeLine(context.registries(), stack).ifPresent(builder);
        // A blank carrier says nothing extra: there is no store to fill and nothing to fire.
        if (inscribed.isEmpty()) return;
        String key = TalismanService.ready(stack)
                ? "tooltip.mxt.talisman.ready" : "tooltip.mxt.talisman.charging";
        builder.accept(Component.translatable(key).withStyle(ChatFormatting.DARK_AQUA));
        // The bar only exists once the component does, and a carrier a pack wrote itself carries none until it is
        // first used, so the number is read off the definitions and says "full" until then.
        int durability = TalismanService.durability(stack);
        if (durability > 0)
            builder.accept(Component.translatable("tooltip.mxt.talisman.durability",
                    Math.max(0, durability - stack.getDamageValue()), durability).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.mxt.talisman.mode." + TalismanService.mode(stack).key()));
    }

    // How that kind of talisman is used, once per type on the carrier. A type whose key is not translated shows
    // nothing at all, so a pack that explains nothing never leaves a raw key on a tooltip; the id comes from the
    // registry the type's own codec is registered under, so nothing here repeats a type name by hand.
    private static Optional<Component> usageLine(TalismanType type, Set<Identifier> explained) {
        Identifier id = MxtRegistries.TALISMAN_TYPE.getKey(type.codec());
        if (!explained.add(id)) return Optional.empty();
        MutableComponent line = Component.translatable(DefinitionText.key(USAGE_CATEGORY, TYPE_REGISTRY_NAMESPACE, id));
        return DefinitionText.resolved(line) ? Optional.of(line.withStyle(ChatFormatting.GRAY)) : Optional.empty();
    }
}
