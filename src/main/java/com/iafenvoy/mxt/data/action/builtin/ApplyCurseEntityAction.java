package com.iafenvoy.mxt.data.action.builtin;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.curse.CurseService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/**
 * Applies a datapack curse through the authoritative curse transaction.
 */
public record ApplyCurseEntityAction(Identifier curse, NumberProvider stacks, Optional<NumberProvider> durationTicks) implements EntityAction {
    public static final MapCodec<ApplyCurseEntityAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("curse").forGetter(ApplyCurseEntityAction::curse),
            NumberProvider.CODEC.optionalFieldOf("stacks", new Constant(1.0D)).forGetter(ApplyCurseEntityAction::stacks),
            NumberProvider.CODEC.optionalFieldOf("duration_ticks").forGetter(ApplyCurseEntityAction::durationTicks)
    ).apply(instance, ApplyCurseEntityAction::new));

    @Override
    public void execute(Entity entity) {
        this.execute(entity, FormulaContext.EMPTY);
    }

    @Override
    public void execute(Entity entity, FormulaContext context) {
        double resolvedStacks = this.stacks.evaluate(context);
        if (!Double.isFinite(resolvedStacks) || resolvedStacks < 1.0D || resolvedStacks > 256.0D) return;
        Optional<Long> duration = this.durationTicks.flatMap(provider -> {
            double value = provider.evaluate(context);
            return Double.isFinite(value) && value >= 0.0D && value <= Long.MAX_VALUE ? Optional.of(Math.round(value)) : Optional.empty();
        });
        MxtDatapackRegistries.get(MxtDatapackRegistries.CURSE, this.curse).ifPresent(definition ->
                CurseService.applyWithDuration(entity, this.curse, definition, (int) Math.round(resolvedStacks), entity.level().getGameTime(), context, "ability", duration));
    }

    @Override
    public MapCodec<ApplyCurseEntityAction> codec() {
        return CODEC;
    }
}
