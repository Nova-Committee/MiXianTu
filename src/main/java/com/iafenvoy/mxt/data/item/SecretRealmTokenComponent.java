package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.secretrealm.SecretRealm;
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
 * Datapack-selected secret realm carried by an access token.
 */
@EventBusSubscriber(Dist.CLIENT)
public record SecretRealmTokenComponent(Optional<Holder<SecretRealm>> realm) implements TooltipProvider {
    public static final SecretRealmTokenComponent EMPTY = new SecretRealmTokenComponent(Optional.empty());
    public static final Codec<SecretRealmTokenComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryFixedCodec.create(MxtResourceKeys.SECRET_REALM).optionalFieldOf("realm").forGetter(SecretRealmTokenComponent::realm)
    ).apply(i, SecretRealmTokenComponent::new));

    @SubscribeEvent
    public static void registerTooltipAppender(RegisterTooltipAppendersEvent event) {
        event.registerComponentAppenderBeforeAll(MxtDataComponents.SECRET_REALM_TOKEN, TooltipAppender.createComponentAppender(MxtDataComponents.SECRET_REALM_TOKEN.get()));
    }

    @Override
    public void addToTooltip(@NonNull TooltipContext context, Consumer<Component> consumer, @NonNull TooltipFlag flag, @NonNull DataComponentGetter components) {
        consumer.accept(Component.translatable("tooltip.mxt.secret_realm_token.realm", this.realm.map(value -> DefinitionText.name(value, "secret_realm")).orElse(Component.literal("-"))));
    }
}
