package com.iafenvoy.mxt.event.handler;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.AttributeModifier;
import com.iafenvoy.mxt.data.action.builtin.entity.GrantSpiritRootAction;
import com.iafenvoy.mxt.data.item.PillBinding;
import com.iafenvoy.mxt.data.item.WeaponBinding;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.Locale;

/**
 * Adds readable datapack binding details to existing-item tooltips.
 */
@EventBusSubscriber(modid = MiXianTu.MOD_ID, value = Dist.CLIENT)
public final class DataDrivenItemTooltipEvents {
    private DataDrivenItemTooltipEvents() {
    }

    @SubscribeEvent
    public static void appendDetails(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Provider registries = event.getContext().registries();
        ItemBindingService.weapon(registries, stack).ifPresent(weapon -> appendWeapon(event, weapon));
        ItemBindingService.pill(registries, stack).ifPresent(pill -> appendPill(event, pill));
        ItemBindingService.actions(registries, stack).stream()
                .filter(GrantSpiritRootAction.class::isInstance)
                .map(GrantSpiritRootAction.class::cast)
                .forEach(action -> appendSpiritRoot(event, action));
    }

    private static void appendSpiritRoot(ItemTooltipEvent event, GrantSpiritRootAction action) {
        event.getToolTip().add(Component.translatable("tooltip.mxt.item.spirit_root").withStyle(ChatFormatting.AQUA));
        if (event.getFlags().isAdvanced()) event.getToolTip().add(
                Component.literal(action.spiritRoot().toString()).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void appendWeapon(ItemTooltipEvent event, WeaponBinding weapon) {
        event.getToolTip().add(Component.translatable("tooltip.mxt.item.weapon").withStyle(ChatFormatting.GOLD));
        event.getToolTip().add(Component.translatable("tooltip.mxt.weapon.attack_damage",
                number(weapon.attackDamage().evaluate(FormulaContext.EMPTY))).withStyle(ChatFormatting.BLUE));
        event.getToolTip().add(Component.translatable("tooltip.mxt.weapon.attack_speed",
                number(weapon.attackSpeed().evaluate(FormulaContext.EMPTY))).withStyle(ChatFormatting.BLUE));
        for (AttributeModifier attribute : weapon.attributes()) {
            {
                Attribute value = attribute.attribute().value();
                double amount = attribute.value().evaluate(FormulaContext.EMPTY);
                if (!Double.isFinite(amount) || amount == 0.0D) return;
                Component name = Component.translatable(value.getDescriptionId());
                switch (attribute.operation()) {
                    case ADD_VALUE -> event.getToolTip().add(Component.translatable("tooltip.mxt.weapon.attribute.add",
                            signed(amount), name).withStyle(ChatFormatting.BLUE));
                    case ADD_MULTIPLIED_BASE ->
                            event.getToolTip().add(Component.translatable("tooltip.mxt.weapon.attribute.multiply_base",
                                    signed(amount * 100.0D), name).withStyle(ChatFormatting.BLUE));
                    case ADD_MULTIPLIED_TOTAL ->
                            event.getToolTip().add(Component.translatable("tooltip.mxt.weapon.attribute.multiply_total",
                                    signed(amount * 100.0D), name).withStyle(ChatFormatting.BLUE));
                }
            }
        }
    }

    private static void appendPill(ItemTooltipEvent event, PillBinding pill) {
        double gain = pill.toxicityGain().evaluate(FormulaContext.EMPTY);
        double threshold = pill.toxicityThreshold().evaluate(FormulaContext.EMPTY);
        event.getToolTip().add(Component.translatable("tooltip.mxt.item.pill").withStyle(ChatFormatting.LIGHT_PURPLE));
        if (threshold >= Double.MAX_VALUE / 2.0D) {
            event.getToolTip().add(Component.translatable("tooltip.mxt.pill.toxicity_no_threshold", signed(gain))
                    .withStyle(ChatFormatting.DARK_PURPLE));
        } else {
            event.getToolTip().add(Component.translatable("tooltip.mxt.pill.toxicity", signed(gain), number(threshold))
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("\\.00$", "").replaceAll("(\\.\\d)0$", "$1");
    }

    private static String signed(double value) {
        return (value >= 0.0D ? "+" : "") + number(value);
    }
}
