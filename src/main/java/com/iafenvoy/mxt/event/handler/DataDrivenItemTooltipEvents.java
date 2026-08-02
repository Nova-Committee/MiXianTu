package com.iafenvoy.mxt.event.handler;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.alchemy.PillDefinition;
import com.iafenvoy.mxt.data.common.AttributeModifierDefinition;
import com.iafenvoy.mxt.data.item.SpiritRootItemEffect;
import com.iafenvoy.mxt.data.weapon.WeaponDefinition;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.runtime.item.ItemBindingService.ResolvedEffect;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.Locale;

/**
 * Adds readable datapack effect details to data-driven item tooltips.
 */
@EventBusSubscriber(modid = MiXianTu.MOD_ID, value = Dist.CLIENT)
public final class DataDrivenItemTooltipEvents {
    private DataDrivenItemTooltipEvents() {
    }

    @SubscribeEvent
    public static void appendDetails(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        HolderLookup.Provider registries = event.getContext().registries();
        boolean advanced = event.getFlags().isAdvanced();
        if (advanced) ItemBindingService.find(registries, stack).ifPresent(item -> event.getToolTip().add(
                Component.translatable("tooltip.mxt.item.definition", item.reference().id().toString()).withStyle(ChatFormatting.DARK_GRAY)));
        for (ResolvedEffect effect : ItemBindingService.effects(registries, stack)) {
            switch (effect.definition()) {
                case WeaponDefinition weapon -> appendWeapon(event, weapon);
                case PillDefinition pill -> appendPill(event, pill);
                case SpiritRootItemEffect root -> appendSpiritRoot(event, root, advanced);
                default -> appendUnknownEffect(event, effect, advanced);
            }
        }
    }

    private static void appendSpiritRoot(ItemTooltipEvent event, SpiritRootItemEffect root, boolean advanced) {
        event.getToolTip().add(Component.translatable("tooltip.mxt.item.spirit_root").withStyle(ChatFormatting.AQUA));
        if (advanced) event.getToolTip().add(
                Component.translatable("tooltip.mxt.item.effect", root.spiritRoot().toString()).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void appendUnknownEffect(ItemTooltipEvent event, ResolvedEffect effect, boolean advanced) {
        event.getToolTip().add(Component.translatable("tooltip.mxt.item.unknown_effect").withStyle(ChatFormatting.GRAY));
        if (advanced) event.getToolTip().add(
                Component.translatable("tooltip.mxt.item.effect", effect.id().toString()).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void appendWeapon(ItemTooltipEvent event, WeaponDefinition weapon) {
        event.getToolTip().add(Component.translatable("tooltip.mxt.item.weapon").withStyle(ChatFormatting.GOLD));
        event.getToolTip().add(Component.translatable("tooltip.mxt.weapon.attack_damage",
                number(weapon.attackDamage().evaluate(FormulaContext.EMPTY))).withStyle(ChatFormatting.BLUE));
        event.getToolTip().add(Component.translatable("tooltip.mxt.weapon.attack_speed",
                number(weapon.attackSpeed().evaluate(FormulaContext.EMPTY))).withStyle(ChatFormatting.BLUE));
        for (AttributeModifierDefinition attribute : weapon.attributes()) {
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

    private static void appendPill(ItemTooltipEvent event, PillDefinition pill) {
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
