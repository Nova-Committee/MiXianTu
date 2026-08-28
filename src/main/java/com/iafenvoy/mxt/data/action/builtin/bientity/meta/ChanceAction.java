package com.iafenvoy.mxt.data.action.builtin.bientity.meta;

import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

public record ChanceAction(BiEntityAction action, float chance,
                           Optional<BiEntityAction> failAction) implements BiEntityAction {
    public static final MapCodec<ChanceAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiEntityAction.CODEC.fieldOf("action").forGetter(ChanceAction::action),
            Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(ChanceAction::chance),
            BiEntityAction.CODEC.optionalFieldOf("fail_action").forGetter(ChanceAction::failAction)
    ).apply(i, ChanceAction::new));

    @Override
    public void execute(BiEntityActionContext ctx) {
        Entity actor = ctx.actor();
        Entity target = ctx.target();
        FormulaContext context = ctx.formula();
        if (actor.getRandom().nextFloat() < this.chance) this.action.execute(actor, target, ctx);
        else this.failAction.ifPresent(action -> action.execute(actor, target, ctx));
    }

    @Override
    public MapCodec<ChanceAction> codec() {
        return CODEC;
    }
}
