package com.iafenvoy.mxt.data.formation;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.runtime.formation.FormationActionContext;

/**
 * The three beat modules differ only in which beat they answer, so the two-armed dispatch is written once: the block
 * action is the array's own and the entity action is the arriving, standing or leaving entity's.
 */
public interface PhaseAction {
    BlockAction blockAction();

    EntityAction entityAction();

    default void run(FormationActionContext context) {
        if (context.perEntity()) this.entityAction().execute(context.entityContext());
        else this.blockAction().execute(context.block());
    }
}
