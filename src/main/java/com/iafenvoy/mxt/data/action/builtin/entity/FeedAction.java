package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.context.action.EntityActionContext;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public record FeedAction(int food, float saturation) implements EntityAction {
    public static final MapCodec<FeedAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("food").forGetter(FeedAction::food),
            Codec.FLOAT.fieldOf("saturation").forGetter(FeedAction::saturation)
    ).apply(i, FeedAction::new));

    @Override
    public void execute(EntityActionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        if (entity instanceof Player player) player.getFoodData().eat(this.food, this.saturation);
    }

    @Override
    public MapCodec<FeedAction> codec() {
        return CODEC;
    }
}
