package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jspecify.annotations.NonNull;

public record EmitGameEventAction(Holder<GameEvent> event) implements EntityAction {
    public static final MapCodec<EmitGameEventAction> CODEC = GameEvent.CODEC.fieldOf("event").xmap(EmitGameEventAction::new, EmitGameEventAction::event);

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        ctx.entity().gameEvent(this.event);
    }

    @Override
    public @NonNull MapCodec<EmitGameEventAction> codec() {
        return CODEC;
    }
}
