package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.runtime.element.ElementReactionService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import org.jspecify.annotations.NonNull;

/**
 * Builds up one element on the acting entity, starting a reaction without a strike (lava bath, pill, curse).
 * A negative amount takes buildup away, so the same action cleanses.
 */
public record AttachElementAction(Holder<Element> element, NumberProvider amount) implements EntityAction {
    public static final MapCodec<AttachElementAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Element.CODEC.fieldOf("element").forGetter(AttachElementAction::element),
            NumberProvider.CODEC.fieldOf("amount").forGetter(AttachElementAction::amount)
    ).apply(i, AttachElementAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        FormulaContext context = ctx.formula();
        double amount = this.amount.evaluate(context);
        if (!Double.isFinite(amount) || amount == 0.0D) return;
        ElementReactionService.apply(ctx.entity(), this.element, amount, context);
    }

    @Override
    public @NonNull MapCodec<AttachElementAction> codec() {
        return CODEC;
    }
}
