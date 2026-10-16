package com.iafenvoy.mxt.data.formation.builtin;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.formation.FormationActionType;
import com.iafenvoy.mxt.data.formation.PhaseAction;
import com.iafenvoy.mxt.runtime.formation.FormationActionContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The generic closing beat: a block action for the array when it comes down, an entity action for every entity that
 * leaves it. The array's own half runs after the entities it was tracking have been released, because a block action
 * cannot see them.
 */
public record InactivateFormationAction(BlockAction blockAction,
                                        EntityAction entityAction) implements FormationActionType, PhaseAction {
    public static final MapCodec<InactivateFormationAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BlockAction.optionalCodec("block_action").forGetter(InactivateFormationAction::blockAction),
            EntityAction.optionalCodec("entity_action").forGetter(InactivateFormationAction::entityAction)
    ).apply(i, InactivateFormationAction::new));

    @Override
    public MapCodec<InactivateFormationAction> codec() {
        return CODEC;
    }

    @Override
    public void inactivate(FormationActionContext context) {
        this.run(context);
    }
}
