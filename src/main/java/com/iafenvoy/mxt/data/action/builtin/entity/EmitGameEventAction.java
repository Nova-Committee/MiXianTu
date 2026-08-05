package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gameevent.GameEvent;

public record EmitGameEventAction(Holder<GameEvent> event) implements EntityAction {
    public static final MapCodec<EmitGameEventAction> CODEC = GameEvent.CODEC.fieldOf("event").xmap(EmitGameEventAction::new, EmitGameEventAction::event);

    @Override
    public void execute(Entity entity, FormulaContext context) {
        entity.gameEvent(this.event);
    }

    @Override
    public MapCodec<EmitGameEventAction> codec() {
        return CODEC;
    }
}
