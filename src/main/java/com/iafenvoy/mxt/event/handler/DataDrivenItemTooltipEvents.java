package com.iafenvoy.mxt.event.handler;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.AttributeModifier;
import com.iafenvoy.mxt.data.action.builtin.entity.GrantSpiritRootAction;
import com.iafenvoy.mxt.data.item.PillBinding;
import com.iafenvoy.mxt.data.item.WeaponBinding;
import com.iafenvoy.mxt.data.item.ContractScrollData;
import com.iafenvoy.mxt.data.item.FormationPlateData;
import com.iafenvoy.mxt.data.item.RealmTokenData;
import com.iafenvoy.mxt.data.item.ResourceContainerData;
import com.iafenvoy.mxt.data.item.SpiritBeastData;
import com.iafenvoy.mxt.data.item.TokenData;
import com.iafenvoy.mxt.data.aura.ItemAura;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.runtime.cultivation.ItemAuraService;
import com.iafenvoy.mxt.util.HolderHelper;
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
        ItemAuraService.find(registries, stack).ifPresent(itemAura -> appendItemAura(event, itemAura.value()));
        ItemBindingService.weapon(registries, stack).ifPresent(weapon -> appendWeapon(event, weapon));
        ItemBindingService.pill(registries, stack).ifPresent(pill -> appendPill(event, pill));
        ItemBindingService.actions(registries, stack).stream()
                .filter(GrantSpiritRootAction.class::isInstance)
                .map(GrantSpiritRootAction.class::cast)
                .forEach(action -> appendSpiritRoot(event, action));
        appendFrameworkState(event, stack);
    }

    private static void appendItemAura(ItemTooltipEvent event, ItemAura itemAura) {
        double total = itemAura.totalAura().evaluate(FormulaContext.EMPTY);
        double speed = itemAura.consumeSpeed().evaluate(FormulaContext.EMPTY);
        event.getToolTip().add(Component.translatable("tooltip.mxt.item.item_aura").withStyle(ChatFormatting.AQUA));
        event.getToolTip().add(Component.translatable("tooltip.mxt.item_aura.total", number(total))
                .withStyle(ChatFormatting.BLUE));
        event.getToolTip().add(Component.translatable("tooltip.mxt.item_aura.speed", number(speed))
                .withStyle(ChatFormatting.BLUE));
        itemAura.resultStack().ifPresent(template -> event.getToolTip().add(Component.translatable(
                "tooltip.mxt.item_aura.result", template.create().getHoverName()).withStyle(ChatFormatting.BLUE)));
    }

    private static void appendFrameworkState(ItemTooltipEvent event, ItemStack stack) {
        ContractScrollData contract = stack.get(MxtDataComponents.CONTRACT_SCROLL);
        if (contract != null) event.getToolTip().add(Component.translatable("tooltip.mxt.contract_scroll.type",
                contract.contractType().map(HolderHelper::id).map(Object::toString).orElse("-")));
        SpiritBeastData beast = stack.get(MxtDataComponents.SPIRIT_BEAST);
        if (beast != null) event.getToolTip().add(Component.translatable("tooltip.mxt.spirit_beast_bag.state",
                beast.entity().isPresent() ? Component.translatable("tooltip.mxt.filled") : Component.translatable("tooltip.mxt.empty")));
        FormationPlateData formation = stack.get(MxtDataComponents.FORMATION_PLATE);
        if (formation != null) event.getToolTip().add(Component.translatable("tooltip.mxt.formation_plate.formation",
                formation.formation().map(HolderHelper::id).map(Object::toString).orElse("-")));
        RealmTokenData realm = stack.get(MxtDataComponents.REALM_TOKEN);
        if (realm != null) event.getToolTip().add(Component.translatable("tooltip.mxt.realm_token.realm",
                realm.realm().map(HolderHelper::id).map(Object::toString).orElse("-")));
        ResourceContainerData container = stack.get(MxtDataComponents.RESOURCE_CONTAINER);
        if (container != null) container.values().forEach((resource, amount) -> event.getToolTip().add(
                Component.translatable("tooltip.mxt.spirit_vessel.resource", HolderHelper.id(resource), number(amount))));
        TokenData token = stack.get(MxtDataComponents.TOKEN);
        if (token != null && token.kind().isPresent())
            event.getToolTip().add(Component.translatable("tooltip.mxt.token",
                    token.kind().orElseThrow(), token.value().orElse("-")));
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
