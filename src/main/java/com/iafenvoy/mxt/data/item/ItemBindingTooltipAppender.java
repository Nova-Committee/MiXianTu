package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.action.builtin.entity.GrantSpiritRootAction;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.runtime.item.ItemBindingService.ResolvedBindings;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.TooltipText;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.ai.attributes.Attribute;
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

import java.util.List;
import java.util.function.Consumer;

/**
 * Adds data-driven weapon, pill, technique and item-action details.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class ItemBindingTooltipAppender {
    private ItemBindingTooltipAppender() {
    }

    @SubscribeEvent
    public static void register(RegisterTooltipAppendersEvent event) {
        event.registerAppender(TooltipLocation.POST_CUSTOM, ItemBindingTooltipAppender::appendBindings);
    }

    private static void appendBindings(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                       Player player, TooltipFlag flag, Consumer<Component> builder) {
        Provider registries = context.registries();
        if (registries == null) return;
        ResolvedBindings bindings = ItemBindingService.resolve(registries, stack);
        bindings.weapon().ifPresent(weapon -> appendWeapon(builder, weapon));
        bindings.pill().ifPresent(pill -> appendPill(builder, pill));
        bindings.technique().ifPresent(technique -> appendTechnique(builder, technique));
        bindings.item().map(ItemBinding::actions).orElse(List.of()).stream()
                .filter(GrantSpiritRootAction.class::isInstance)
                .map(GrantSpiritRootAction.class::cast)
                .forEach(action -> appendSpiritRoot(builder, flag, action));
        if (player != null) {
            FormulaContext formula = FormulaContext.of(player);
            bindings.item().ifPresent(binding -> appendConditions(builder, binding.conditions(), player, formula));
            bindings.weapon().ifPresent(binding -> appendConditions(builder, binding.conditions(), player, formula));
            bindings.pill().ifPresent(binding -> appendConditions(builder, binding.conditions(), player, formula));
            bindings.technique().ifPresent(binding -> appendConditions(builder, binding.conditions(), player, formula));
        }
    }

    private static void appendConditions(Consumer<Component> builder, List<ConditionEntry> conditions,
                                         Player player, FormulaContext formula) {
        for (ConditionEntry entry : conditions) {
            entry.description().ifPresent(description -> {
                boolean met = entry.condition().test(player, formula);
                MutableComponent marker = Component.literal(met ? "✔ " : "✖ ")
                        .withStyle(met ? ChatFormatting.GREEN : ChatFormatting.RED);
                builder.accept(marker.append(Component.translatable(description)));
            });
        }
    }

    private static void appendSpiritRoot(Consumer<Component> builder, TooltipFlag flag, GrantSpiritRootAction action) {
        builder.accept(Component.translatable("tooltip.mxt.item.spirit_root").withStyle(ChatFormatting.AQUA));
        if (flag.isAdvanced())
            builder.accept(DefinitionText.name(action.spiritRoot(), "spirit_root").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void appendWeapon(Consumer<Component> builder, WeaponBinding weapon) {
        builder.accept(Component.translatable("tooltip.mxt.item.weapon").withStyle(ChatFormatting.GOLD));
        builder.accept(Component.translatable("tooltip.mxt.weapon.attack_damage",
                TooltipText.number(weapon.attackDamage().evaluate(FormulaContext.EMPTY))).withStyle(ChatFormatting.BLUE));
        builder.accept(Component.translatable("tooltip.mxt.weapon.attack_speed",
                TooltipText.number(weapon.attackSpeed().evaluate(FormulaContext.EMPTY))).withStyle(ChatFormatting.BLUE));
        for (AttributeEntry attribute : weapon.attributes()) {
            Attribute value = attribute.attribute().value();
            double amount = attribute.amount(FormulaContext.EMPTY);
            if (!Double.isFinite(amount) || amount == 0.0D) continue;
            Component name = Component.translatable(value.getDescriptionId());
            switch (attribute.modifier().operation()) {
                case ADD_VALUE -> builder.accept(Component.translatable("tooltip.mxt.weapon.attribute.add",
                        TooltipText.signed(amount), name).withStyle(ChatFormatting.BLUE));
                case ADD_MULTIPLIED_BASE ->
                        builder.accept(Component.translatable("tooltip.mxt.weapon.attribute.multiply_base",
                                TooltipText.signed(amount * 100.0D), name).withStyle(ChatFormatting.BLUE));
                case ADD_MULTIPLIED_TOTAL ->
                        builder.accept(Component.translatable("tooltip.mxt.weapon.attribute.multiply_total",
                                TooltipText.signed(amount * 100.0D), name).withStyle(ChatFormatting.BLUE));
            }
        }
    }

    private static void appendPill(Consumer<Component> builder, PillBinding pill) {
        double gain = pill.toxicityGain().evaluate(FormulaContext.EMPTY);
        double threshold = pill.toxicityThreshold().evaluate(FormulaContext.EMPTY);
        builder.accept(Component.translatable("tooltip.mxt.item.pill").withStyle(ChatFormatting.LIGHT_PURPLE));
        if (threshold >= Double.MAX_VALUE / 2.0D) {
            builder.accept(Component.translatable("tooltip.mxt.pill.toxicity_no_threshold", TooltipText.signed(gain))
                    .withStyle(ChatFormatting.DARK_PURPLE));
        } else {
            builder.accept(Component.translatable("tooltip.mxt.pill.toxicity", TooltipText.signed(gain), TooltipText.number(threshold))
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    private static void appendTechnique(Consumer<Component> builder, TechniqueBinding technique) {
        builder.accept(Component.translatable("tooltip.mxt.item.cultivation_technique",
                DefinitionText.name(technique.technique(), "cultivation_technique")).withStyle(ChatFormatting.GREEN));
    }
}
