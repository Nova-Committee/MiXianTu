package com.iafenvoy.mxt.data.curse;

import com.iafenvoy.mxt.data.action.builtin.entity.ApplyCurseAction;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.util.DefinitionText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.tooltip.TooltipAppender;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.function.Consumer;

/**
 * The curses an item carries. While such a stack is equipped they are applied to its holder, and taking the stack
 * off removes the ones it owns; a curse that lapses in between is applied again, so a carried curse lasts as long
 * as the gear does.
 * <p>
 * Entries are {@code mxt:apply_curse} entries, so a stack cannot declare anything that action could not do, and
 * the item itself never touches the holder's state: everything goes through the ordinary curse transaction.
 * <p>
 * What a stack carries is written on the stack: the tooltip names every curse, because being cursed by gear one
 * chose to wear should not come as a surprise.
 */
@EventBusSubscriber(Dist.CLIENT)
public record CurseContainerComponent(List<ApplyCurseAction> curses) implements TooltipProvider {
    public static final Codec<CurseContainerComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            ApplyCurseAction.CODEC.codec().listOf().optionalFieldOf("curses", List.of()).forGetter(CurseContainerComponent::curses)
    ).apply(i, CurseContainerComponent::new));

    public CurseContainerComponent() {
        this(List.of());
    }

    @SubscribeEvent
    public static void registerTooltipAppender(RegisterTooltipAppendersEvent event) {
        event.registerComponentAppenderBeforeAll(MxtDataComponents.CURSE_CONTAINER, TooltipAppender.createComponentAppender(MxtDataComponents.CURSE_CONTAINER.get()));
    }

    @Override
    public void addToTooltip(@NonNull TooltipContext context, @NonNull Consumer<Component> consumer, @NonNull TooltipFlag flag, @NonNull DataComponentGetter components) {
        if (this.curses.isEmpty()) return;
        consumer.accept(Component.translatable("tooltip.mxt.curse_container").withStyle(ChatFormatting.DARK_RED));
        for (ApplyCurseAction action : this.curses) {
            consumer.accept(Component.literal(" ")
                    .append(DefinitionText.name(action.curse(), "curse"))
                    .withStyle(ChatFormatting.RED));
        }
    }
}
