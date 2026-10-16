package com.iafenvoy.mxt.data.formation.builtin;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.formation.FormationActionType;
import com.iafenvoy.mxt.data.formation.PhaseAction;
import com.iafenvoy.mxt.runtime.formation.FormationActionContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The generic periodic beat: a block action for the array itself once per upkeep period, an entity action for every
 * entity it covers. This is the upkeep-period beat, so a module that wants another rhythm implements {@code interval}
 * instead and writes {@code interval_ticks}.
 */
public record TickFormationAction(BlockAction blockAction,
                                  EntityAction entityAction) implements FormationActionType, PhaseAction {
    public static final MapCodec<TickFormationAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BlockAction.optionalCodec("block_action").forGetter(TickFormationAction::blockAction),
            EntityAction.optionalCodec("entity_action").forGetter(TickFormationAction::entityAction)
    ).apply(i, TickFormationAction::new));

    @Override
    public MapCodec<TickFormationAction> codec() {
        return CODEC;
    }

    @Override
    public void tick(FormationActionContext context) {
        this.run(context);
    }
}
