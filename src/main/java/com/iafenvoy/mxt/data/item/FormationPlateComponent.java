package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFixedCodec;
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
 * Selected formation definition for a portable formation controller.
 */
@EventBusSubscriber(Dist.CLIENT)
public record FormationPlateComponent(Optional<Holder<Formation>> formation) implements TooltipProvider {
    public static final FormationPlateComponent EMPTY = new FormationPlateComponent(Optional.empty());
    public static final Codec<FormationPlateComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryFixedCodec.create(MxtResourceKeys.FORMATION).optionalFieldOf("formation").forGetter(FormationPlateComponent::formation)
    ).apply(i, FormationPlateComponent::new));

    @SubscribeEvent
    public static void registerTooltipAppender(RegisterTooltipAppendersEvent event) {
        event.registerComponentAppenderBeforeAll(MxtDataComponents.FORMATION_PLATE, TooltipAppender.createComponentAppender(MxtDataComponents.FORMATION_PLATE.get()));
    }

    @Override
    public void addToTooltip(@NonNull TooltipContext context, Consumer<Component> consumer, @NonNull TooltipFlag flag, @NonNull DataComponentGetter components) {
        consumer.accept(Component.translatable("tooltip.mxt.formation_plate.formation", this.formation.map(value -> DefinitionText.name(value, "formation")).orElse(Component.literal("-"))));
    }
}
