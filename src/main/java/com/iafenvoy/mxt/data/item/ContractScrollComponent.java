package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.data.creature.ContractType;
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
 * Datapack-selected contract policy carried by a reusable contract scroll.
 */
@EventBusSubscriber(modid = MiXianTu.MOD_ID, value = Dist.CLIENT)
public record ContractScrollComponent(Optional<Holder<ContractType>> contractType) implements TooltipProvider {
    public static final ContractScrollComponent EMPTY = new ContractScrollComponent(Optional.empty());
    public static final Codec<ContractScrollComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryFixedCodec.create(MxtResourceKeys.CONTRACT_TYPE).optionalFieldOf("contract_type").forGetter(ContractScrollComponent::contractType)
    ).apply(i, ContractScrollComponent::new));

    @SubscribeEvent
    public static void registerTooltipAppender(RegisterTooltipAppendersEvent event) {
        event.registerComponentAppenderBeforeAll(MxtDataComponents.CONTRACT_SCROLL, TooltipAppender.createComponentAppender(MxtDataComponents.CONTRACT_SCROLL.get()));
    }

    @Override
    public void addToTooltip(@NonNull TooltipContext context, Consumer<Component> consumer, @NonNull TooltipFlag flag, @NonNull DataComponentGetter components) {
        consumer.accept(Component.translatable("tooltip.mxt.contract_scroll.type", this.contractType.map(HolderHelper::id).map(Object::toString).orElse("-")));
    }
}
