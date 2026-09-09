package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.recipe.AlchemyRecipe;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

import java.util.LinkedList;
import java.util.List;

public abstract class AlchemyCraftEvent extends Event {
    private final RecipeHolder<AlchemyRecipe> recipe;

    protected AlchemyCraftEvent(RecipeHolder<AlchemyRecipe> recipe) {
        this.recipe = recipe;
    }

    public RecipeHolder<AlchemyRecipe> recipe() {
        return this.recipe;
    }

    public static final class Pre extends AlchemyCraftEvent implements ICancellableEvent {
        private final List<Identifier> inputs;

        public Pre(RecipeHolder<AlchemyRecipe> recipe, List<Identifier> inputs) {
            super(recipe);
            this.inputs = new LinkedList<>(inputs);
        }

        public List<Identifier> inputs() {
            return this.inputs;
        }
    }

    public static final class Post extends AlchemyCraftEvent {
        private final boolean spoiled;
        private final List<Identifier> outputs;

        public Post(RecipeHolder<AlchemyRecipe> recipe, boolean spoiled, List<Identifier> outputs) {
            super(recipe);
            this.spoiled = spoiled;
            this.outputs = new LinkedList<>(outputs);
        }

        public boolean spoiled() {
            return this.spoiled;
        }

        public List<Identifier> outputs() {
            return this.outputs;
        }
    }
}
