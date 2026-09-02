package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.recipe.AlchemyRecipe;
import com.iafenvoy.mxt.recipe.AlchemyRecipeInput;
import com.iafenvoy.mxt.registry.MxtRecipeTypes;
import com.iafenvoy.mxt.runtime.alchemy.AlchemySession.Failure;
import com.iafenvoy.mxt.runtime.alchemy.AlchemySession.Snapshot;
import com.iafenvoy.mxt.runtime.alchemy.AlchemySession.StartResult;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationService.TickResult;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationService.TickResult.State;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Inventory/UI-neutral contract backed by the vanilla RecipeManager.
 */
public interface AlchemyWorkstation {
    AlchemyWorkstationState alchemyState();

    int furnaceTier();

    double temperature();

    void setChanged();

    default StartResult startAlchemy(Level level, Identifier recipeId, FormulaContext context) {
        StartResult result = find(level, recipeId)
                .map(holder -> AlchemyWorkstationService.start(this.alchemyState(), holder.id().identifier(), holder.value().definition(), this.furnaceTier(), context))
                .orElse(StartResult.rejected(Failure.DISABLED));
        if (result.started()) this.setChanged();
        return result;
    }

    default StartResult startAlchemy(Level level, FormulaContext context) {
        if (!(level instanceof ServerLevel serverLevel)) return StartResult.rejected(Failure.DISABLED);
        return serverLevel.getServer().getRecipeManager().getRecipeFor(MxtRecipeTypes.ALCHEMY.get(),
                        new AlchemyRecipeInput(this.alchemyState().inputs()), level)
                .map(holder -> AlchemyWorkstationService.start(this.alchemyState(), holder.id().identifier(), holder.value().definition(), this.furnaceTier(), context))
                .map(result -> {
                    if (result.started()) this.setChanged();
                    return result;
                })
                .orElse(StartResult.rejected(Failure.INPUTS));
    }

    default TickResult tickAlchemy(Level level, FormulaContext context) {
        Identifier recipeId = this.alchemyState().session().map(Snapshot::recipe).orElse(null);
        if (recipeId == null) return TickResult.idle();
        TickResult result = find(level, recipeId)
                .map(holder -> AlchemyWorkstationService.tick(this.alchemyState(), holder.value().definition(), this.temperature(), context))
                .orElse(TickResult.invalidOutput(false));
        if (result.state() != State.IDLE) this.setChanged();
        return result;
    }

    default TickResult tickAlchemy(Level level, BlockPos pos, FormulaContext context) {
        Identifier recipeId = this.alchemyState().session().map(Snapshot::recipe).orElse(null);
        if (recipeId == null) return TickResult.idle();
        TickResult result = find(level, recipeId)
                .map(holder -> AlchemyWorkstationService.tick(level, pos, this.alchemyState(), holder.value().definition(), this.temperature(), context))
                .orElse(TickResult.invalidOutput(false));
        if (result.state() != State.IDLE) this.setChanged();
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Optional<RecipeHolder<AlchemyRecipe>> find(Level level, Identifier id) {
        if (!(level instanceof ServerLevel serverLevel)) return Optional.empty();
        return serverLevel.getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, id))
                .filter(holder -> holder.value() instanceof AlchemyRecipe)
                .map(holder -> (RecipeHolder<AlchemyRecipe>) holder);
    }
}
