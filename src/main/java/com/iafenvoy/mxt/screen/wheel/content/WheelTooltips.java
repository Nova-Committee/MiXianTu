package com.iafenvoy.mxt.screen.wheel.content;

import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.ResourceCost;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.screen.wheel.WheelDuration;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How a wheel entry's numbers are written down, shared by both entry kinds. Values are resolved with the
 * client's context, so a tooltip shows this player's numbers, and durations use {@link WheelDuration}.
 */
final class WheelTooltips {
    // How values are separated inside one line.
    private static final Component SEPARATOR = Component.literal("、");

    private WheelTooltips() {
    }

    static Component number(double value) {
        if (!Double.isFinite(value)) return Component.literal("0");
        if (value == Math.rint(value) && Math.abs(value) < 1.0E15D)
            return Component.literal(Long.toString((long) value));
        return Component.literal(String.format(Locale.ROOT, "%.1f", value));
    }

    // Only ResourceCost is spelled out - other types become "something else", never a guess - and each is
    // evaluated in the context of the resource it charges, which is the context the server charges it in.
    static Component costs(List<Cost> costs, Player player) {
        FormulaContext base = FormulaContext.of(player);
        List<Component> parts = new ArrayList<>(costs.size());
        boolean other = false;
        for (Cost cost : costs) {
            if (cost instanceof ResourceCost resourceCost) {
                FormulaContext context = ResourceService.formulaContext(player, resourceCost.id(),
                        resourceCost.resource().value(), base);
                parts.add(Component.translatable("wheel.mxt.tooltip.cost_part",
                        DefinitionText.name(resourceCost.resource()), number(resourceCost.amount().evaluate(context))));
            } else {
                other = true;
            }
        }
        if (other) parts.add(Component.translatable("wheel.mxt.tooltip.cost_other"));
        return join(parts);
    }

    static Component elements(List<Either<Holder<Element>, TagKey<Element>>> affinity) {
        List<Component> parts = new ArrayList<>(affinity.size());
        for (Either<Holder<Element>, TagKey<Element>> entry : affinity) {
            parts.add(entry.map(DefinitionText::name, tag -> Component.literal("#" + tag.location())));
        }
        return join(parts);
    }

    private static Component join(List<Component> parts) {
        MutableComponent joined = Component.empty();
        for (int index = 0; index < parts.size(); index++) {
            if (index > 0) joined.append(SEPARATOR);
            joined.append(parts.get(index));
        }
        return joined;
    }
}
