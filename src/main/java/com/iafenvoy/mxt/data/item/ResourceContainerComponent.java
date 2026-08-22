package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMaps;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.core.Holder;
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
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.TooltipText;
import org.jspecify.annotations.NonNull;

import java.util.function.Consumer;

/**
 * Portable, resource-agnostic energy storage used by stones, batteries and future artifacts.
 */
@EventBusSubscriber(Dist.CLIENT)
public record ResourceContainerComponent(Object2DoubleMap<Holder<Resource>> values) implements TooltipProvider {
    public static final Codec<ResourceContainerComponent> CODEC = CollectionCodecs.doubleMap(Resource.CODEC).xmap(ResourceContainerComponent::new, ResourceContainerComponent::values);
    public static final ResourceContainerComponent EMPTY = new ResourceContainerComponent(Object2DoubleMaps.emptyMap());

    public ResourceContainerComponent(Object2DoubleMap<Holder<Resource>> values) {
        this.values = new Object2DoubleOpenHashMap<>();
        values.forEach((resource, value) -> {
            if (resource == null || !Double.isFinite(value) || value < 0.0D)
                throw new IllegalArgumentException("Stored resource values must be finite and non-negative");
            if (value > 0.0D) this.values.put(resource, value);
        });
    }

    public ResourceContainerComponent with(Holder<Resource> resource, double value) {
        Object2DoubleMap<Holder<Resource>> next = new Object2DoubleOpenHashMap<>(this.values);
        if (!Double.isFinite(value) || value < 0.0D)
            throw new IllegalArgumentException("Stored resource value must be finite and non-negative");
        if (value == 0.0D) next.removeDouble(resource);
        else next.put(resource, value);
        return new ResourceContainerComponent(next);
    }

    @SubscribeEvent
    public static void registerTooltipAppender(RegisterTooltipAppendersEvent event) {
        event.registerComponentAppenderBeforeAll(MxtDataComponents.RESOURCE_CONTAINER, TooltipAppender.createComponentAppender(MxtDataComponents.RESOURCE_CONTAINER.get()));
    }

    @Override
    public void addToTooltip(@NonNull TooltipContext context, @NonNull Consumer<Component> consumer, @NonNull TooltipFlag flag, @NonNull DataComponentGetter components) {
        this.values.forEach((resource, amount) -> consumer.accept(Component.translatable(
                "tooltip.mxt.spirit_vessel.resource", HolderHelper.id(resource), TooltipText.number(amount))));
    }
}
