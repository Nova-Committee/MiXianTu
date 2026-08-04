package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public record ExhaustAction(float amount) implements EntityAction {
    public static final MapCodec<ExhaustAction> CODEC = Codec.FLOAT.fieldOf("amount").xmap(ExhaustAction::new, ExhaustAction::amount);

    @Override
    public void execute(Entity entity) {
        if (entity instanceof Player player && this.amount > 0.0F) player.causeFoodExhaustion(this.amount);
    }

    @Override
    public MapCodec<ExhaustAction> codec() {
        return CODEC;
    }
}
