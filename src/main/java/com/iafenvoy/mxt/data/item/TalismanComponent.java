package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.Talisman;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
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
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * The talismans written onto one carrier, in append order, and how this carrier answers being filled. Writing a
 * talisman appends rather than replaces, so one carrier can hold several; an empty list is a blank carrier. The
 * mode lives on the stack, not on any definition, and {@link TriggerMode#FIRE} is the default - which is also
 * what every carrier written before this field existed decodes as.
 */
@EventBusSubscriber(Dist.CLIENT)
public record TalismanComponent(List<Holder<Talisman>> talismans, TriggerMode mode) implements TooltipProvider {
    public static final TalismanComponent EMPTY = new TalismanComponent(List.of(), TriggerMode.FIRE);
    public static final Codec<TalismanComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryFixedCodec.create(MxtResourceKeys.TALISMAN).listOf().optionalFieldOf("talismans", List.of())
                    .forGetter(TalismanComponent::talismans),
            TriggerMode.CODEC.optionalFieldOf("mode", TriggerMode.FIRE).forGetter(TalismanComponent::mode)
    ).apply(i, TalismanComponent::new));

    // FIRE fires the moment the bill is paid, which is what makes a carrier a one-shot; STORE only accumulates
    // and waits to be fired by a hand or switched back.
    public enum TriggerMode {
        FIRE("fire"),
        STORE("store");

        public static final Codec<TriggerMode> CODEC = Codec.STRING.comapFlatMap(
                name -> Arrays.stream(values()).filter(mode -> mode.key.equals(name)).findFirst()
                        .map(DataResult::success)
                        .orElseGet(() -> DataResult.error(() -> "Unknown talisman mode: " + name)),
                mode -> mode.key);

        private final String key;

        TriggerMode(String key) {
            this.key = key;
        }

        public String key() {
            return this.key;
        }

        public TriggerMode other() {
            return this == FIRE ? STORE : FIRE;
        }
    }

    // The appended definition is kept as written: a carrier may hold the same talisman twice, so "write it again"
    // is a no-op the crafting loop decides on rather than a rule hidden here.
    public TalismanComponent appended(Holder<Talisman> talisman) {
        List<Holder<Talisman>> appended = new ArrayList<>(this.talismans);
        appended.add(talisman);
        return new TalismanComponent(List.copyOf(appended), this.mode);
    }

    public TalismanComponent withMode(TriggerMode mode) {
        return new TalismanComponent(this.talismans, mode);
    }

    @SubscribeEvent
    public static void registerTooltipAppender(RegisterTooltipAppendersEvent event) {
        event.registerComponentAppenderBeforeAll(MxtDataComponents.TALISMAN, TooltipAppender.createComponentAppender(MxtDataComponents.TALISMAN.get()));
    }

    @Override
    public void addToTooltip(@NonNull TooltipContext context, @NonNull Consumer<Component> consumer, @NonNull TooltipFlag flag, @NonNull DataComponentGetter components) {
        if (this.talismans.isEmpty()) {
            consumer.accept(Component.translatable("tooltip.mxt.talisman.empty"));
            return;
        }
        consumer.accept(Component.translatable("tooltip.mxt.talisman.mode." + this.mode.key()));
        for (Holder<Talisman> talisman : this.talismans)
            consumer.accept(Component.translatable("tooltip.mxt.talisman.entry", DefinitionText.name(talisman, "talisman")));
    }
}
