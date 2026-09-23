package com.iafenvoy.mxt.screen.wheel.content;

import com.iafenvoy.mxt.data.cost.Charge;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostFailure;
import com.iafenvoy.mxt.data.cost.context.CostOrigin;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.screen.wheel.WheelDuration;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.TooltipText;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How a wheel entry's numbers are written down, shared by both entry kinds. Values are resolved with the
 * client's context, so a tooltip shows this player's numbers, and durations use {@link WheelDuration}.
 */
final class WheelTooltips {
    private WheelTooltips() {
    }

    static Component number(double value) {
        if (!Double.isFinite(value)) return Component.literal("0");
        if (value == Math.rint(value) && Math.abs(value) < 1.0E15D)
            return Component.literal(Long.toString((long) value));
        return Component.literal(String.format(Locale.ROOT, "%.1f", value));
    }

    // Only the resource channel is spelled out - everything else becomes "something else", never a guess - and
    // each amount comes from the same charge the server would collect, in the context it would collect it in.
    static Component costs(List<Cost> costs, Player player) {
        CostContext context = CostContext.of(player, CostOrigin.ABILITY);
        List<Component> parts = new ArrayList<>(costs.size());
        boolean other = false;
        for (Cost cost : costs) {
            Either<Charge, CostFailure> charge = cost.charge(context);
            if (charge.left().isEmpty() || !(charge.left().get() instanceof Charge.Resources(
                    Map<Identifier, Double> amounts
            ))) {
                other = true;
                continue;
            }
            amounts.forEach((id, amount) -> MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, id)
                    .ifPresent(resource -> parts.add(Component.translatable("wheel.mxt.tooltip.cost_part",
                            DefinitionText.name(resource), number(amount)))));
        }
        if (other) parts.add(Component.translatable("wheel.mxt.tooltip.cost_other"));
        return TooltipText.join(parts);
    }

    static Component elements(List<Either<Holder<Element>, TagKey<Element>>> affinity) {
        List<Component> parts = new ArrayList<>(affinity.size());
        for (Either<Holder<Element>, TagKey<Element>> entry : affinity) {
            parts.add(entry.map(DefinitionText::name, tag -> Component.literal("#" + tag.location())));
        }
        return TooltipText.join(parts);
    }
}
