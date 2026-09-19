package com.iafenvoy.mxt.data.action.builtin.item;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.context.action.ItemActionContext;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NonNull;

/**
 * Charges one artifact from a declared amount.
 * <p>
 * The declared capacity is the fallback rather than the ceiling: a stack that names an archetype is measured
 * against the capacity that archetype declares, so this number only decides how much a stack whose archetype is
 * missing or no longer resolves can hold.
 */
public record ChargeArtifactItemAction(NumberProvider amount, NumberProvider capacity) implements ItemAction {
    public static final MapCodec<ChargeArtifactItemAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("amount").forGetter(ChargeArtifactItemAction::amount),
            NumberProvider.CODEC.fieldOf("capacity").forGetter(ChargeArtifactItemAction::capacity)
    ).apply(i, ChargeArtifactItemAction::new));

    @Override
    public void execute(@NonNull ItemActionContext ctx) {
        FormulaContext context = ctx.formula();
        ArtifactService.addEnergy(ctx.stack(), this.amount.evaluate(context), this.capacity.evaluate(context), context);
    }

    @Override
    public @NonNull MapCodec<ChargeArtifactItemAction> codec() {
        return CODEC;
    }
}
