package com.iafenvoy.mxt.data.aura;

import com.iafenvoy.mxt.runtime.spirit.SpiritChargeService;
import com.iafenvoy.mxt.runtime.spirit.SpiritChargeService.Charge;
import com.iafenvoy.mxt.util.TooltipText;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.tooltip.TooltipLocation;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Displays the current charge of item stacks that implement {@code ItemAuraAccess}. The reading itself is
 * {@link SpiritChargeService}'s, so what is shown here is the same number the pour writes and the item
 * conditions test.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class SpiritStorageTooltipAppender {
    @SubscribeEvent
    public static void register(RegisterTooltipAppendersEvent event) {
        event.registerAppender(TooltipLocation.POST_CUSTOM, SpiritStorageTooltipAppender::appendCharge);
    }

    private static void appendCharge(ItemStack stack, TooltipContext context, TooltipDisplay display, Player player, TooltipFlag flag, Consumer<Component> builder) {
        Provider registries = context.registries();
        if (registries == null) return;

        Optional<Charge> charge = resolveCharge(registries, stack, FormulaContext.EMPTY);
        if (charge.isEmpty()) return;
        int percentage = charge.get().percentage();
        Component percent = Component.literal(percentage + "%").withColor(colorValue(percentage));
        builder.accept(Component.translatable("tooltip.mxt.spirit_storage", TooltipText.number(charge.get().stored()), TooltipText.number(charge.get().capacity()), percent));
    }

    public static Optional<Charge> resolveCharge(Provider registries, ItemStack stack, FormulaContext context) {
        return Optional.ofNullable(SpiritChargeService.resolve(registries, stack, context));
    }

    public static int colorValue(int percentage) {
        return SpiritChargeService.color(percentage);
    }
}
