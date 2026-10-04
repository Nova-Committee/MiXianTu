package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.DescribedEntry;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.builtin.entity.GrantPhysiqueAction;
import com.iafenvoy.mxt.data.action.builtin.entity.GrantSpiritRootAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.runtime.item.ItemBindingService.ResolvedBindings;
import com.iafenvoy.mxt.runtime.item.PillService;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.TooltipText;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
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
import org.jspecify.annotations.Nullable;

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
        ItemBindingService.PillResolution resolution = bindings.pill();
        if (resolution.unbound())
            builder.accept(Component.translatable("tooltip.mxt.pill.unbound").withStyle(ChatFormatting.RED));
        else
            resolution.effects().ifPresent(effects -> appendPill(builder, resolution.identity().orElse(null), effects, player));
        bindings.technique().ifPresent(technique -> appendTechnique(builder, technique));
        for (EntityAction action : bindings.item().map(ItemBinding::actions).orElse(List.of())) {
            if (action instanceof GrantSpiritRootAction(
                    Holder<SpiritRoot> spiritRoot
            ))
                appendGranted(builder, flag, "tooltip.mxt.item.spirit_root", HolderHelper.id(spiritRoot));
            else if (action instanceof GrantPhysiqueAction(
                    Holder<Physique> physique1
            ))
                appendGranted(builder, flag, "tooltip.mxt.item.physique", HolderHelper.id(physique1));
        }
        if (player != null) {
            FormulaContext formula = FormulaContext.of(player);
            bindings.item().ifPresent(binding -> appendConditions(builder, binding.conditions(), player, formula));
            bindings.weapon().ifPresent(binding -> appendConditions(builder, binding.conditions(), player, formula));
            bindings.pill().effects().ifPresent(pill -> appendConditions(builder, pill.conditions(), player, formula));
            bindings.technique().ifPresent(binding -> appendConditions(builder, binding.conditions(), player, formula));
        }
    }

    private static void appendConditions(Consumer<Component> builder, List<DescribedEntry<EntityCondition>> conditions,
                                         Player player, FormulaContext formula) {
        for (DescribedEntry<EntityCondition> entry : conditions) {
            entry.description().ifPresent(description -> {
                boolean met = entry.value().test(player, formula);
                MutableComponent marker = Component.literal(met ? "✔ " : "✖ ")
                        .withStyle(met ? ChatFormatting.GREEN : ChatFormatting.RED);
                builder.accept(marker.append(Component.translatable(description)));
            });
        }
    }

    // The line says what a click grants; the advanced half always spells out the entry id, never the display text
    // the line above already stands for.
    private static void appendGranted(Consumer<Component> builder, TooltipFlag flag, String key, Identifier id) {
        builder.accept(Component.translatable(key).withStyle(ChatFormatting.AQUA));
        if (flag.isAdvanced())
            builder.accept(Component.literal("   " + id).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void appendWeapon(Consumer<Component> builder, WeaponBinding weapon) {
        builder.accept(Component.translatable("tooltip.mxt.item.weapon").withStyle(ChatFormatting.GOLD));
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

    private static void appendPill(Consumer<Component> builder, @Nullable Holder<PillBinding> identity, Pill effects,
                                   Player player) {
        if (DefinitionText.resolved(effects.name()))
            builder.accept(effects.name().copy().withStyle(ChatFormatting.LIGHT_PURPLE));
        if (DefinitionText.resolved(effects.description()) && !effects.description().getString().isBlank())
            builder.accept(effects.description().copy().withStyle(ChatFormatting.GRAY));
        double gain = effects.toxicityGain().evaluate(FormulaContext.EMPTY);
        double threshold = effects.toxicityThreshold().evaluate(FormulaContext.EMPTY);
        if (threshold >= Double.MAX_VALUE / 2.0D) {
            builder.accept(Component.translatable("tooltip.mxt.pill.toxicity_no_threshold", TooltipText.signed(gain))
                    .withStyle(ChatFormatting.DARK_PURPLE));
        } else {
            builder.accept(Component.translatable("tooltip.mxt.pill.toxicity", TooltipText.signed(gain), TooltipText.number(threshold))
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        // Limits stay on the bound definition. An overlay must not hide or replace them.
        if (identity == null || !identity.isBound()) return;
        PillBinding limits = identity.value();
        int taken = player == null ? 0 : PillService.uses(player, identity);
        if (limits.maxUses().isPresent()) {
            builder.accept(Component.translatable("tooltip.mxt.pill.max_uses", taken, limits.maxUses().orElseThrow())
                    .withStyle(ChatFormatting.DARK_PURPLE));
        } else if (taken > 0) {
            builder.accept(Component.translatable("tooltip.mxt.pill.uses", taken).withStyle(ChatFormatting.DARK_PURPLE));
        }
        double cooldown = limits.cooldown().evaluate(FormulaContext.EMPTY);
        if (Double.isFinite(cooldown) && cooldown > 0.0D)
            builder.accept(Component.translatable("tooltip.mxt.pill.cooldown", TooltipText.number(cooldown))
                    .withStyle(ChatFormatting.DARK_PURPLE));
        if (player != null && PillService.onCooldown(player, identity)) {
            long left = Math.max(0L, PillService.cooldownUntil(player, identity) - PillService.overworldGameTime(player));
            builder.accept(Component.translatable("tooltip.mxt.pill.cooldown_remaining", left)
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    private static void appendTechnique(Consumer<Component> builder, TechniqueBinding technique) {
        builder.accept(Component.translatable("tooltip.mxt.item.technique",
                DefinitionText.name(technique.technique(), "technique")).withStyle(ChatFormatting.GREEN));
    }
}
