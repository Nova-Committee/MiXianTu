package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.rift.RiftPreset;
import com.iafenvoy.mxt.data.secretrealm.SecretRealm;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Datapack-selected secret realm carried by an access token.
 */
@EventBusSubscriber(Dist.CLIENT)
public record SecretRealmTokenComponent(Optional<Holder<SecretRealm>> realm,
                                        Optional<Holder<RiftPreset>> preset, long durationTicks,
                                        Optional<UUID> summoned,
                                        Optional<Identifier> summonedDimension) implements TooltipProvider {
    public static final SecretRealmTokenComponent EMPTY = new SecretRealmTokenComponent(Optional.empty());
    public static final Codec<SecretRealmTokenComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            SecretRealm.CODEC.optionalFieldOf("realm").forGetter(SecretRealmTokenComponent::realm),
            RiftPreset.CODEC.optionalFieldOf("preset").forGetter(SecretRealmTokenComponent::preset),
            MiscCodecs.longRange(0, Long.MAX_VALUE).optionalFieldOf("duration_ticks", 0L).forGetter(SecretRealmTokenComponent::durationTicks),
            UUIDUtil.CODEC.optionalFieldOf("summoned").forGetter(SecretRealmTokenComponent::summoned),
            Identifier.CODEC.optionalFieldOf("summoned_dimension").forGetter(SecretRealmTokenComponent::summonedDimension)
    ).apply(i, SecretRealmTokenComponent::new));

    public SecretRealmTokenComponent(Optional<Holder<SecretRealm>> realm) {
        this(realm, Optional.empty(), 0, Optional.empty(), Optional.empty());
    }

    @SubscribeEvent
    public static void registerTooltipAppender(RegisterTooltipAppendersEvent event) {
        event.registerComponentAppenderBeforeAll(MxtDataComponents.SECRET_REALM_TOKEN, TooltipAppender.createComponentAppender(MxtDataComponents.SECRET_REALM_TOKEN.get()));
    }

    @Override
    public void addToTooltip(@NonNull TooltipContext context, Consumer<Component> consumer, @NonNull TooltipFlag flag, @NonNull DataComponentGetter components) {
        consumer.accept(Component.translatable("tooltip.mxt.secret_realm_token.realm", this.realm.map(DefinitionText::nameOrId).orElse(Component.literal("-"))));
    }
}
