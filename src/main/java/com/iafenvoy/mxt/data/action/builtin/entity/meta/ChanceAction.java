package com.iafenvoy.mxt.data.action.builtin.entity.meta;

import com.iafenvoy.mxt.data.context.action.EntityActionContext;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

public record ChanceAction(EntityAction action, float chance,
                           EntityAction failAction) implements EntityAction {
    public static final MapCodec<ChanceAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            EntityAction.CODEC.fieldOf("action").forGetter(ChanceAction::action),
            Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(ChanceAction::chance),
            EntityAction.optionalCodec("fail_action").forGetter(ChanceAction::failAction)
    ).apply(i, ChanceAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        if (entity.getRandom().nextFloat() < this.chance) this.action.execute(entity, ctx);
        else this.failAction.execute(entity, ctx);
    }

    @Override
    public @NonNull MapCodec<ChanceAction> codec() {
        return CODEC;
    }
}
