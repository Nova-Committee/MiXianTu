package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.data.RealmInstance;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.resources.RegistryFixedCodec;
import com.iafenvoy.mxt.util.HolderHelper;
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
 * Datapack-selected realm instance carried by an access token.
 */
@EventBusSubscriber(modid = MiXianTu.MOD_ID, value = Dist.CLIENT)
public record RealmTokenComponent(Optional<Holder<RealmInstance>> realm) implements TooltipProvider {
    public static final RealmTokenComponent EMPTY = new RealmTokenComponent(Optional.empty());
    public static final Codec<RealmTokenComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryFixedCodec.create(MxtResourceKeys.REALM_INSTANCE).optionalFieldOf("realm").forGetter(RealmTokenComponent::realm)
    ).apply(i, RealmTokenComponent::new));

    @SubscribeEvent
    public static void registerTooltipAppender(RegisterTooltipAppendersEvent event) {
        event.registerComponentAppenderBeforeAll(MxtDataComponents.REALM_TOKEN, TooltipAppender.createComponentAppender(MxtDataComponents.REALM_TOKEN.get()));
    }

    @Override
    public void addToTooltip(@NonNull TooltipContext context, Consumer<Component> consumer, @NonNull TooltipFlag flag, @NonNull DataComponentGetter components) {
        consumer.accept(Component.translatable("tooltip.mxt.realm_token.realm", this.realm.map(HolderHelper::id).map(Object::toString).orElse("-")));
    }
}
