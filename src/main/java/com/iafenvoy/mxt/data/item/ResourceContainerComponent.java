package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.TooltipText;
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
import org.jspecify.annotations.NonNull;

import java.util.function.Consumer;

/**
 * Portable, resource-agnostic energy storage for the item that carries it: a vessel rather than a battery. It holds
 * whatever the holder's own pool holds, fractional values included, and pours them back into that pool while it is
 * held down. How much of each resource fits lives next to it in {@code mxt:resource_capacity}; whole-unit stores are a
 * different thing and live in {@code SpiritStorageComponent} under the {@code ItemAuraAccess} protocol, whose capacity
 * comes from the {@code item_aura} definition instead.
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

    public double stored(Holder<Resource> resource) {
        return this.values.getOrDefault(resource, 0.0D);
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
        ResourceCapacityComponent capacity = components.getOrDefault(MxtDataComponents.RESOURCE_CAPACITY, ResourceCapacityComponent.EMPTY);
        this.values.forEach((resource, amount) -> consumer.accept(line(resource, amount, capacity.capacityOf(resource))));
        // A resource with a cap and nothing stored still gets its line: that is how big the vessel is.
        for (Object2DoubleMap.Entry<Holder<Resource>> entry : capacity.values().object2DoubleEntrySet()) {
            if (this.values.containsKey(entry.getKey())) continue;
            consumer.accept(line(entry.getKey(), 0.0D, entry.getDoubleValue()));
        }
    }

    // The amount is always shown; the cap joins it as soon as somebody wrote one for that resource.
    private static Component line(Holder<Resource> resource, double amount, double capacity) {
        return capacity > 0.0D
                ? Component.translatable("tooltip.mxt.spirit_vessel.resource_capped", DefinitionText.name(resource, "resource"), TooltipText.number(amount), TooltipText.number(capacity))
                : Component.translatable("tooltip.mxt.spirit_vessel.resource", DefinitionText.name(resource, "resource"), TooltipText.number(amount));
    }
}
