package com.iafenvoy.mxt.data.aura;

import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.cultivation.ItemAuraService;
import com.iafenvoy.mxt.runtime.spirit.SpiritItemAccess;
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
 * Displays the current charge of item stacks that implement {@link SpiritItemAccess}.
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
        Component percent = Component.literal(charge.get().percentage() + "%").withColor(colorValue(charge.get().percentage()));
        builder.accept(Component.translatable("tooltip.mxt.spirit_storage", TooltipText.number(charge.get().stored()), TooltipText.number(charge.get().capacity()), percent));
    }

    public static Optional<Charge> resolveCharge(Provider registries, ItemStack stack, FormulaContext context) {
        if (!(stack.getItem() instanceof SpiritItemAccess)) return Optional.empty();
        int capacity = ItemAuraService.capacity(registries, stack, context);
        if (capacity <= 0) return Optional.empty();
        SpiritStorageComponent component = stack.get(MxtDataComponents.SPIRIT_STORAGE);
        int stored = component == null ? capacity : Math.max(0, Math.min(capacity, component.amount()));
        int percentage = (int) Math.round(stored * 100.0D / capacity);
        return Optional.of(new Charge(stored, capacity, percentage));
    }

    public static int colorValue(int percentage) {
        if (percentage >= 75) return 0xFF55FF55;
        if (percentage >= 50) return 0xFFFFFF55;
        if (percentage >= 25) return 0xFFFFAA00;
        return 0xFFFF5555;
    }

    public record Charge(int stored, int capacity, int percentage) {
    }
}
