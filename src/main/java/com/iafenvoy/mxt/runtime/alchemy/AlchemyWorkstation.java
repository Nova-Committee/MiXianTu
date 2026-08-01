package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.alchemy.AlchemySession.Failure;
import com.iafenvoy.mxt.runtime.alchemy.AlchemySession.Snapshot;
import com.iafenvoy.mxt.runtime.alchemy.AlchemySession.StartResult;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationService.TickResult;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationService.TickResult.State;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;

/**
 * Inventory/UI-neutral contract for an alchemy block entity or another server workstation.
 * Implementations own insertion, extraction and persistence; this helper owns recipe progression.
 */
public interface AlchemyWorkstation {
    AlchemyWorkstationState alchemyState();

    int furnaceTier();

    double temperature();

    void setChanged();

    default StartResult startAlchemy(Identifier recipe, FormulaContext context) {
        StartResult result = MxtDatapackRegistries.get(MxtDatapackRegistries.ALCHEMY_RECIPE, recipe)
                .map(definition -> AlchemyWorkstationService.start(this.alchemyState(), recipe, definition, this.furnaceTier(), context))
                .orElse(StartResult.rejected(Failure.DISABLED));
        if (result.started()) this.setChanged();
        return result;
    }

    default TickResult tickAlchemy(FormulaContext context) {
        Identifier recipe = this.alchemyState().session().map(Snapshot::recipe).orElse(null);
        if (recipe == null) return TickResult.idle();
        TickResult result = MxtDatapackRegistries.get(MxtDatapackRegistries.ALCHEMY_RECIPE, recipe)
                .map(definition -> AlchemyWorkstationService.tick(this.alchemyState(), definition, this.temperature(), context))
                .orElse(TickResult.invalidOutput(false));
        if (result.state() != State.IDLE) this.setChanged();
        return result;
    }
}
