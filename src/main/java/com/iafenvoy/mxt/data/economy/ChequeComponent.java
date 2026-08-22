package com.iafenvoy.mxt.data.economy;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
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
 * Persistent value recorded on a cheque. The value is independent of a particular currency item.
 */
@EventBusSubscriber(modid = MiXianTu.MOD_ID, value = Dist.CLIENT)
public record ChequeComponent(long value, String issuer) implements TooltipProvider {
    public static final ChequeComponent EMPTY = new ChequeComponent(0L, "");
    public static final Codec<ChequeComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            MiscCodecs.longRange(0, Long.MAX_VALUE).fieldOf("value").forGetter(ChequeComponent::value),
            Codec.STRING.optionalFieldOf("issuer", "").forGetter(ChequeComponent::issuer)
    ).apply(i, ChequeComponent::new));

    @SubscribeEvent
    public static void registerTooltipAppender(RegisterTooltipAppendersEvent event) {
        event.registerComponentAppenderBeforeAll(MxtDataComponents.CHEQUE, TooltipAppender.createComponentAppender(MxtDataComponents.CHEQUE.get()));
    }

    @Override
    public void addToTooltip(@NonNull TooltipContext context, @NonNull Consumer<Component> consumer, @NonNull TooltipFlag flag, @NonNull DataComponentGetter components) {
        if (this.value > 0L) consumer.accept(Component.translatable("item.mxt.cheque.value", this.value).withStyle(ChatFormatting.GOLD));
    }
}
