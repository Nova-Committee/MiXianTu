package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.tooltip.TooltipAppender;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;
import org.jspecify.annotations.NonNull;

import java.util.function.Consumer;

/**
 * The result fixed when an unidentified stack is created.
 */
@EventBusSubscriber(Dist.CLIENT)
public record IdentificationComponent(Identifier result,
                                      DataComponentPatch resultComponents) implements TooltipProvider {
    public static final Codec<IdentificationComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("result").forGetter(IdentificationComponent::result),
            DataComponentPatch.CODEC.optionalFieldOf("result_components", DataComponentPatch.EMPTY)
                    .forGetter(IdentificationComponent::resultComponents)
    ).apply(i, IdentificationComponent::new));

    public IdentificationComponent(Identifier result) {
        this(result, DataComponentPatch.EMPTY);
    }

    @SubscribeEvent
    public static void registerTooltipAppender(RegisterTooltipAppendersEvent event) {
        event.registerComponentAppenderBeforeAll(MxtDataComponents.IDENTIFICATION,
                TooltipAppender.createComponentAppender(MxtDataComponents.IDENTIFICATION.get()));
    }

    @Override
    public void addToTooltip(@NonNull TooltipContext context, @NonNull Consumer<Component> consumer,
                             @NonNull TooltipFlag flag, @NonNull DataComponentGetter components) {
        consumer.accept(Component.translatable("tooltip.mxt.identification.unidentified").withStyle(ChatFormatting.GOLD));
        consumer.accept(Component.translatable("tooltip.mxt.identification.hint").withStyle(ChatFormatting.GRAY));
    }
}
