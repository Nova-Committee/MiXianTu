package com.iafenvoy.mxt.screen.wheel.content;

import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.CostPayment;
import com.iafenvoy.mxt.data.cost.context.CostContext;
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
    private WheelTooltips() {
    }

    static Component number(double value) {
        if (!Double.isFinite(value)) return Component.literal("0");
        if (value == Math.rint(value) && Math.abs(value) < 1.0E15D)
            return Component.literal(Long.toString((long) value));
        return Component.literal(String.format(Locale.ROOT, "%.1f", value));
    }

    // Only the resource channel is spelled out - everything else becomes "something else", never a guess - and the
    // amounts are what the server would collect for the whole array, in the context it would collect it in. One pass
    // for all of it, so entries that reach the same resource add up the way they will when it is paid.
    static Component costs(List<Cost> costs, Player player) {
        CostContext context = CostContext.of(player, CostOrigin.ABILITY);
        CostPayment payment = CostPayment.of(context);
        boolean other = false;
        for (Cost cost : costs) if (payment.load(cost).isPresent()) other = true;
        List<Component> parts = new ArrayList<>(costs.size());
        payment.resources().forEach((id, amount) -> MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, id)
                .ifPresent(resource -> parts.add(Component.translatable("wheel.mxt.tooltip.cost_part",
                        DefinitionText.name(resource), number(amount)))));
        if (other || payment.beyondResources()) parts.add(Component.translatable("wheel.mxt.tooltip.cost_other"));
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
