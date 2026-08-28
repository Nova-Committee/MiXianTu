package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.context.action.EntityActionContext;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.runtime.curse.CurseService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/**
 * Applies a datapack curse through the authoritative curse transaction.
 */
public record ApplyCurseAction(Holder<Curse> curse, NumberProvider stacks,
                               Optional<NumberProvider> durationTicks) implements EntityAction {
    public static final MapCodec<ApplyCurseAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Curse.CODEC.fieldOf("curse").forGetter(ApplyCurseAction::curse),
            NumberProvider.CODEC.optionalFieldOf("stacks", new Constant(1.0D)).forGetter(ApplyCurseAction::stacks),
            NumberProvider.CODEC.optionalFieldOf("duration_ticks").forGetter(ApplyCurseAction::durationTicks)
    ).apply(i, ApplyCurseAction::new));

    @Override
    public void execute(EntityActionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        double resolvedStacks = this.stacks.evaluate(context);
        if (!Double.isFinite(resolvedStacks) || resolvedStacks < 1.0D || resolvedStacks > 256.0D) return;
        Optional<Long> duration = this.durationTicks.flatMap(provider -> {
            double value = provider.evaluate(context);
            return Double.isFinite(value) && value >= 0.0D && value <= Long.MAX_VALUE ? Optional.of(Math.round(value)) : Optional.empty();
        });
        CurseService.applyWithDuration(entity, this.curse, (int) Math.round(resolvedStacks), entity.level().getGameTime(), context, "ability", duration);
    }

    @Override
    public MapCodec<ApplyCurseAction> codec() {
        return CODEC;
    }
}
