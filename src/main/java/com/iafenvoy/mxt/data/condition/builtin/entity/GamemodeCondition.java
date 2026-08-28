package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.NonNull;

public record GamemodeCondition(GameType gamemode) implements EntityCondition {
    public static final MapCodec<GamemodeCondition> CODEC = GameType.CODEC.fieldOf("gamemode").xmap(GamemodeCondition::new, GamemodeCondition::gamemode);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        return entity instanceof ServerPlayer player && player.gameMode.getGameModeForPlayer() == this.gamemode;
    }

    @Override
    public @NonNull MapCodec<GamemodeCondition> codec() {
        return CODEC;
    }
}
