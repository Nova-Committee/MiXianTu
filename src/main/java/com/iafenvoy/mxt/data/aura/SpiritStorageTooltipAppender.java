package com.iafenvoy.mxt.data.aura;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.cultivation.ItemAuraService;
import com.iafenvoy.mxt.runtime.spirit.SpiritItemAccess;
import com.iafenvoy.mxt.util.TooltipText;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.ChatFormatting;
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

import java.util.function.Consumer;

/** Displays the current charge of item stacks that implement {@link SpiritItemAccess}. */
@EventBusSubscriber(modid = MiXianTu.MOD_ID, value = Dist.CLIENT)
public final class SpiritStorageTooltipAppender {
    private SpiritStorageTooltipAppender() {
    }

    @SubscribeEvent
    public static void register(RegisterTooltipAppendersEvent event) {
        event.registerAppender(TooltipLocation.POST_CUSTOM, SpiritStorageTooltipAppender::appendCharge);
    }

    private static void appendCharge(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                     Player player, TooltipFlag flag, Consumer<Component> builder) {
        if (!(stack.getItem() instanceof SpiritItemAccess)) return;
        Provider registries = context.registries();
        if (registries == null) return;

        int capacity = ItemAuraService.capacity(registries, stack, FormulaContext.EMPTY);
        if (capacity <= 0) return;
        SpiritStorageComponent component = stack.get(MxtDataComponents.SPIRIT_STORAGE);
        int stored = Math.min(capacity, component == null ? capacity : component.amount());
        int percentage = (int) Math.round(stored * 100.0D / capacity);
        Component percent = Component.literal(percentage + "%").withStyle(color(percentage));
        builder.accept(Component.translatable("tooltip.mxt.spirit_storage",
                TooltipText.number(stored), TooltipText.number(capacity), percent));
    }

    private static ChatFormatting color(int percentage) {
        if (percentage >= 75) return ChatFormatting.GREEN;
        if (percentage >= 50) return ChatFormatting.YELLOW;
        if (percentage >= 25) return ChatFormatting.GOLD;
        return ChatFormatting.RED;
    }
}
