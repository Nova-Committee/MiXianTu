package com.iafenvoy.mxt.data.action.builtin.bientity.meta;

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
    public void execute(Entity actor, Entity target, FormulaContext context) {
        if (actor.getRandom().nextFloat() < this.chance) this.action.execute(actor, target, context);
        else this.failAction.ifPresent(action -> action.execute(actor, target, context));
    }

    @Override
    public MapCodec<ChanceAction> codec() {
        return CODEC;
    }
}
