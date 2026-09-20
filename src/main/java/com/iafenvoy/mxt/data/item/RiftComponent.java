package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.rift.RiftColors;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.tooltip.TooltipAppender;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;
import org.jspecify.annotations.NonNull;

import java.util.function.Consumer;

/**
 * The destination and colour an anchor stamps into a rift.
 *
 * <p>Carrying this on the item is what lets a rift be configured before it exists: one stack describes the rift
 * it will place, and the same stack reconfigures a rift it is used on.
 */
@EventBusSubscriber(Dist.CLIENT)
public record RiftComponent(Identifier target, int color) implements TooltipProvider {
    public static final RiftComponent EMPTY = new RiftComponent(Level.OVERWORLD.identifier(), RiftColors.AUTO);
    public static final Codec<RiftComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("target", Level.OVERWORLD.identifier()).forGetter(RiftComponent::target),
            Codec.INT.optionalFieldOf("color", RiftColors.AUTO).forGetter(RiftComponent::color)
    ).apply(instance, RiftComponent::new));

    @SubscribeEvent
    public static void registerTooltipAppender(RegisterTooltipAppendersEvent event) {
        event.registerComponentAppenderBeforeAll(MxtDataComponents.RIFT, TooltipAppender.createComponentAppender(MxtDataComponents.RIFT.get()));
    }

    public RiftComponent withTarget(Identifier target) {
        return new RiftComponent(target, this.color);
    }

    public RiftComponent withColor(int color) {
        return new RiftComponent(this.target, color);
    }

    @Override
    public void addToTooltip(@NonNull TooltipContext context, Consumer<Component> consumer, @NonNull TooltipFlag flag, @NonNull DataComponentGetter components) {
        consumer.accept(Component.translatable("tooltip.mxt.rift.target", this.target.toString()));
        consumer.accept(this.color == RiftColors.AUTO
                ? Component.translatable("tooltip.mxt.rift.color.auto")
                : Component.translatable("tooltip.mxt.rift.color", RiftColors.format(this.color)));
    }
}
