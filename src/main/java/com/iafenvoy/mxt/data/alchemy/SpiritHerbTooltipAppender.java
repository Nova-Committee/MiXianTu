package com.iafenvoy.mxt.data.alchemy;

import com.iafenvoy.mxt.runtime.alchemy.SpiritHerbService;
import com.iafenvoy.mxt.runtime.alchemy.SpiritHerbService.HerbRole;
import com.iafenvoy.mxt.util.DefinitionText;
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

import java.util.Map;
import java.util.function.Consumer;

/**
 * Age and role potency, using the same resolver the furnace uses. A stack with no component shows the definition's
 * default age, which is what a furnace would read.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class SpiritHerbTooltipAppender {
    private SpiritHerbTooltipAppender() {
    }

    @SubscribeEvent
    public static void register(RegisterTooltipAppendersEvent event) {
        event.registerAppender(TooltipLocation.POST_CUSTOM, SpiritHerbTooltipAppender::append);
    }

    private static void append(ItemStack stack, TooltipContext context, TooltipDisplay display, Player player,
                               TooltipFlag flag, Consumer<Component> builder) {
        Provider registries = context.registries();
        if (registries == null) return;
        SpiritHerbService.find(registries, stack).ifPresent(herb -> {
            builder.accept(herb.name().copy().withStyle(ChatFormatting.GREEN));
            builder.accept(Component.translatable("tooltip.mxt.herb.age", SpiritHerbService.age(stack, herb))
                    .withStyle(ChatFormatting.GRAY));
            builder.accept(Component.translatable("tooltip.mxt.herb.thermal", herb.thermalBias())
                    .withStyle(ChatFormatting.GRAY));
            FormulaContext formula = player == null ? FormulaContext.EMPTY : FormulaContext.of(player);
            role(builder, registries, stack, HerbRole.MAIN, "tooltip.mxt.herb.main", formula);
            role(builder, registries, stack, HerbRole.AUXILIARY, "tooltip.mxt.herb.auxiliary", formula);
            role(builder, registries, stack, HerbRole.CATALYST, "tooltip.mxt.herb.catalyst", formula);
        });
    }

    private static void role(Consumer<Component> builder, Provider registries, ItemStack stack, HerbRole role,
                             String key, FormulaContext formula) {
        SpiritHerbService.potency(registries, stack, role, formula).ifPresent(potency -> {
            if (potency.totalPower() <= 0.0D) return;
            builder.accept(Component.translatable(key, format(potency.totalPower())).withStyle(ChatFormatting.DARK_GREEN));
            for (Map.Entry<net.minecraft.core.Holder<MedicinalProperty>, Double> entry : potency.properties().entrySet())
                builder.accept(Component.translatable("tooltip.mxt.herb.property",
                        DefinitionText.name(entry.getKey()), format(entry.getValue())).withStyle(ChatFormatting.DARK_GRAY));
        });
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Long.toString(Math.round(value)) : Double.toString(value);
    }
}
