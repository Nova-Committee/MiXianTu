package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.creature.ContractBehavior;
import com.iafenvoy.mxt.data.creature.ContractBehaviors;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The beast a taming bell is tuned to, and the orders that beast takes. The choice lives on the tool because two
 * bells may name two different beasts, and the orders are a snapshot of the creature's own answer taken when it
 * was picked - which is what lets the owner's wheel draw the page without resolving the creature on the client.
 */
@EventBusSubscriber(Dist.CLIENT)
public record ContractBellComponent(UUID beast, Component name, List<Identifier> behaviors) implements TooltipProvider {
    public static final Codec<ContractBellComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("beast").forGetter(ContractBellComponent::beast),
            ComponentSerialization.CODEC.fieldOf("name").forGetter(ContractBellComponent::name),
            Identifier.CODEC.listOf().optionalFieldOf("behaviors", List.of()).forGetter(ContractBellComponent::behaviors)
    ).apply(i, ContractBellComponent::new));

    @SubscribeEvent
    public static void registerTooltipAppender(RegisterTooltipAppendersEvent event) {
        event.registerComponentAppenderBeforeAll(MxtDataComponents.CONTRACT_BELL, TooltipAppender.createComponentAppender(MxtDataComponents.CONTRACT_BELL.get()));
    }

    // The stored ids as orders: one a content mod no longer provides is dropped rather than shown as a raw id,
    // because it can no longer be ordered either.
    public List<ContractBehavior> orders() {
        return this.behaviors.stream().map(ContractBehaviors::byId).flatMap(Optional::stream).toList();
    }

    public boolean offers(Identifier id) {
        return this.behaviors.contains(id);
    }

    @Override
    public void addToTooltip(@NonNull TooltipContext context, Consumer<Component> consumer, @NonNull TooltipFlag flag, @NonNull DataComponentGetter components) {
        consumer.accept(Component.translatable("tooltip.mxt.contract_bell.target", this.name));
        List<ContractBehavior> orders = this.orders();
        if (orders.isEmpty()) return;
        MutableComponent joined = Component.literal("");
        for (ContractBehavior behavior : orders) {
            if (!joined.getString().isEmpty()) joined.append(Component.literal(" / "));
            joined.append(behavior.name());
        }
        consumer.accept(Component.translatable("tooltip.mxt.contract_bell.behaviors", joined));
    }
}
