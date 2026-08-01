package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.data.alchemy.AlchemyRecipeDefinition;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

import java.util.List;

public abstract class AlchemyCraftEvent extends Event {
    private final Identifier recipe;
    private final AlchemyRecipeDefinition definition;

    protected AlchemyCraftEvent(Identifier recipe, AlchemyRecipeDefinition definition) {
        this.recipe = recipe;
        this.definition = definition;
    }

    public Identifier recipe() {
        return this.recipe;
    }

    public AlchemyRecipeDefinition definition() {
        return this.definition;
    }

    public static final class Pre extends AlchemyCraftEvent implements ICancellableEvent {
        private final List<Identifier> inputs;

        public Pre(Identifier recipe, AlchemyRecipeDefinition definition, List<Identifier> inputs) {
            super(recipe, definition);
            this.inputs = List.copyOf(inputs);
        }

        public List<Identifier> inputs() {
            return this.inputs;
        }
    }

    public static final class Post extends AlchemyCraftEvent {
        private final boolean spoiled;
        private final List<Identifier> outputs;

        public Post(Identifier recipe, AlchemyRecipeDefinition definition, boolean spoiled, List<Identifier> outputs) {
            super(recipe, definition);
            this.spoiled = spoiled;
            this.outputs = List.copyOf(outputs);
        }

        public boolean spoiled() {
            return this.spoiled;
        }

        public List<Identifier> outputs() {
            return this.outputs;
        }
    }
}
