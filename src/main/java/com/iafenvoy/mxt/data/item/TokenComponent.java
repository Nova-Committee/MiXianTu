package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.MiXianTu;
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

/**
 * Generic identity and permission payload for sect, realm and trading tokens.
 */
@EventBusSubscriber(Dist.CLIENT)
public record TokenComponent(Optional<String> kind, Optional<String> value,
                             Optional<String> owner) implements TooltipProvider {
    public static final TokenComponent EMPTY = new TokenComponent(Optional.empty(), Optional.empty(), Optional.empty());
    public static final Codec<TokenComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("kind").forGetter(TokenComponent::kind),
            Codec.STRING.optionalFieldOf("value").forGetter(TokenComponent::value),
            Codec.STRING.optionalFieldOf("owner").forGetter(TokenComponent::owner)
    ).apply(i, TokenComponent::new));

    @SubscribeEvent
    public static void registerTooltipAppender(RegisterTooltipAppendersEvent event) {
        event.registerComponentAppenderBeforeAll(MxtDataComponents.TOKEN, TooltipAppender.createComponentAppender(MxtDataComponents.TOKEN.get()));
    }

    @Override
    public void addToTooltip(@NonNull TooltipContext context, @NonNull Consumer<Component> consumer, @NonNull TooltipFlag flag, @NonNull DataComponentGetter components) {
        this.kind.ifPresent(kind -> consumer.accept(Component.translatable("tooltip.mxt.token", kind, this.value.orElse("-"))));
    }
}
