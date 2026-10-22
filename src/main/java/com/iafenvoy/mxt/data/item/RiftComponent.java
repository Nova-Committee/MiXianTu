package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.rift.RiftDestination;
import com.iafenvoy.mxt.data.rift.RiftTemplate;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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

import java.util.Optional;
import java.util.function.Consumer;

@EventBusSubscriber(Dist.CLIENT)
public record RiftComponent(Optional<RiftTemplate> blueprint,
                            Optional<RiftDestination> recorded) implements TooltipProvider {
    public static final RiftComponent EMPTY = new RiftComponent(Optional.empty(), Optional.empty());
    public static final Codec<RiftComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RiftTemplate.CODEC.optionalFieldOf("blueprint").forGetter(RiftComponent::blueprint),
            RiftDestination.CODEC.optionalFieldOf("recorded").forGetter(RiftComponent::recorded)
    ).apply(instance, RiftComponent::new));

    @SubscribeEvent
    public static void registerTooltipAppender(RegisterTooltipAppendersEvent event) {
        event.registerComponentAppenderBeforeAll(MxtDataComponents.RIFT, TooltipAppender.createComponentAppender(MxtDataComponents.RIFT.get()));
    }

    @Override
    public void addToTooltip(@NonNull TooltipContext context, Consumer<Component> consumer, @NonNull TooltipFlag flag, @NonNull DataComponentGetter components) {
        consumer.accept(Component.translatable("tooltip.mxt.rift.editor"));
    }
}
