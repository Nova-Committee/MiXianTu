package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.nbt.CompoundTag;
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

/**
 * Serialized contract creature retained by a spirit beast bag.
 */
@EventBusSubscriber(Dist.CLIENT)
public record SpiritBeastComponent(Optional<CompoundTag> entity) implements TooltipProvider {
    public static final SpiritBeastComponent EMPTY = new SpiritBeastComponent(Optional.empty());
    public static final Codec<SpiritBeastComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            CompoundTag.CODEC.optionalFieldOf("entity").forGetter(SpiritBeastComponent::entity)
    ).apply(i, SpiritBeastComponent::new));

    @SubscribeEvent
    public static void registerTooltipAppender(RegisterTooltipAppendersEvent event) {
        event.registerComponentAppenderBeforeAll(MxtDataComponents.SPIRIT_BEAST, TooltipAppender.createComponentAppender(MxtDataComponents.SPIRIT_BEAST.get()));
    }

    @Override
    public void addToTooltip(@NonNull TooltipContext context, Consumer<Component> consumer, @NonNull TooltipFlag flag, @NonNull DataComponentGetter components) {
        consumer.accept(Component.translatable("tooltip.mxt.spirit_beast_bag.state",
                this.entity.isPresent() ? Component.translatable("tooltip.mxt.filled") : Component.translatable("tooltip.mxt.empty")));
    }
}
