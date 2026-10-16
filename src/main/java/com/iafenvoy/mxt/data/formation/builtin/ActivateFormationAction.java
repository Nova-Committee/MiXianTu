package com.iafenvoy.mxt.data.formation.builtin;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.formation.FormationActionType;
import com.iafenvoy.mxt.data.formation.PhaseAction;
import com.iafenvoy.mxt.runtime.formation.FormationActionContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The generic opening beat: a block action for the array itself, an entity action for every entity that arrives -
 * which includes everything already inside when the array is raised, hence the idempotence the docs ask for. Both
 * fields are optional, and an absent one is the no-op the removed top-level field used to mean.
 */
public record ActivateFormationAction(BlockAction blockAction,
                                      EntityAction entityAction) implements FormationActionType, PhaseAction {
    public static final MapCodec<ActivateFormationAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BlockAction.optionalCodec("block_action").forGetter(ActivateFormationAction::blockAction),
            EntityAction.optionalCodec("entity_action").forGetter(ActivateFormationAction::entityAction)
    ).apply(i, ActivateFormationAction::new));

    @Override
    public MapCodec<ActivateFormationAction> codec() {
        return CODEC;
    }

    @Override
    public void activate(FormationActionContext context) {
        this.run(context);
    }
}
