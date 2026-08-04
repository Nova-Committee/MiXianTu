package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;

public record GamemodeCondition(GameType gamemode) implements EntityCondition {
    public static final MapCodec<GamemodeCondition> CODEC = GameType.CODEC.fieldOf("gamemode").xmap(GamemodeCondition::new, GamemodeCondition::gamemode);

    @Override
    public boolean test(Entity entity) {
        return entity instanceof ServerPlayer player && player.gameMode.getGameModeForPlayer() == this.gamemode;
    }

    @Override
    public MapCodec<GamemodeCondition> codec() {
        return CODEC;
    }
}
