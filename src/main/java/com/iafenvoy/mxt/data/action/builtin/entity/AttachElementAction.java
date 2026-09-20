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
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

/**
 * Builds up one element on the acting entity, which is how a reaction is started by something other than a
 * strike: a bath in a lava pool, a pill, a curse that keeps feeding fire into a body.
 *
 * <p>A negative amount takes buildup away, so the same action cleanses. What it does when the buildup crosses
 * a reaction's demand is not this action's business - it goes through the same pipeline a strike does.</p>
 */
public record AttachElementAction(Holder<Element> element, NumberProvider amount) implements EntityAction {
    public static final MapCodec<AttachElementAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Element.CODEC.fieldOf("element").forGetter(AttachElementAction::element),
            NumberProvider.CODEC.fieldOf("amount").forGetter(AttachElementAction::amount)
    ).apply(i, AttachElementAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        double amount = this.amount.evaluate(context);
        if (!Double.isFinite(amount) || amount == 0.0D) return;
        ElementReactionService.apply(entity, this.element, amount, context);
    }

    @Override
    public @NonNull MapCodec<AttachElementAction> codec() {
        return CODEC;
    }
}
