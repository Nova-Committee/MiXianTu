package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.Talisman;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The talismans written onto one carrier, in the order they were appended. An empty list is the blank
 * carrier a fresh talisman item starts as; writing a talisman appends its definition rather than replacing
 * what is already there, so one carrier can hold several.
 */
@EventBusSubscriber(Dist.CLIENT)
public record TalismanComponent(List<Holder<Talisman>> talismans) implements TooltipProvider {
    public static final TalismanComponent EMPTY = new TalismanComponent(List.of());
    public static final Codec<TalismanComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryFixedCodec.create(MxtResourceKeys.TALISMAN).listOf().optionalFieldOf("talismans", List.of()).forGetter(TalismanComponent::talismans)
    ).apply(i, TalismanComponent::new));

    /**
     * The same list with one more talisman at the end. The appended definition is kept as written: a
     * carrier may hold the same talisman twice, which is what makes "write it again" a no-op the crafting
     * loop can decide on rather than a rule hidden here.
     */
    public TalismanComponent appended(Holder<Talisman> talisman) {
        List<Holder<Talisman>> appended = new ArrayList<>(this.talismans);
        appended.add(talisman);
        return new TalismanComponent(List.copyOf(appended));
    }

    @SubscribeEvent
    public static void registerTooltipAppender(RegisterTooltipAppendersEvent event) {
        event.registerComponentAppenderBeforeAll(MxtDataComponents.TALISMAN, TooltipAppender.createComponentAppender(MxtDataComponents.TALISMAN.get()));
    }

    @Override
    public void addToTooltip(@NonNull TooltipContext context, Consumer<Component> consumer, @NonNull TooltipFlag flag, @NonNull DataComponentGetter components) {
        if (this.talismans.isEmpty()) {
            consumer.accept(Component.translatable("tooltip.mxt.talisman.empty"));
            return;
        }
        for (Holder<Talisman> talisman : this.talismans)
            consumer.accept(Component.translatable("tooltip.mxt.talisman.entry", DefinitionText.name(talisman, "talisman")));
    }
}
