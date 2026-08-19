package com.iafenvoy.mxt.data.action.builtin.entity.meta;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

public record ChanceAction(EntityAction action, float chance,
                           Optional<EntityAction> failAction) implements EntityAction {
    public static final MapCodec<ChanceAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            EntityAction.CODEC.fieldOf("action").forGetter(ChanceAction::action),
            Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(ChanceAction::chance),
            EntityAction.CODEC.optionalFieldOf("fail_action").forGetter(ChanceAction::failAction)
    ).apply(i, ChanceAction::new));

    @Override
    public void execute(Entity entity, FormulaContext context) {
        if (entity.getRandom().nextFloat() < this.chance) this.action.execute(entity, context);
        else this.failAction.ifPresent(action -> action.execute(entity, context));
    }

    @Override
    public MapCodec<ChanceAction> codec() {
        return CODEC;
    }
}
