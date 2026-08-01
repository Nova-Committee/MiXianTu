package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.data.ability.AbilityDefinition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;

import java.util.Map;

/**
 * Authoritative ability-use events. Only the server calls the ability service.
 */
public abstract class AbilityUseEvent extends EntityEvent {
    private final Identifier ability;
    private final AbilityDefinition definition;
    private final FormulaContext context;

    protected AbilityUseEvent(Entity entity, Identifier ability, AbilityDefinition definition, FormulaContext context) {
        super(entity);
        this.ability = ability;
        this.definition = definition;
        this.context = context;
    }

    public Identifier ability() {
        return this.ability;
    }

    public AbilityDefinition definition() {
        return this.definition;
    }

    public FormulaContext context() {
        return this.context;
    }

    public static final class Pre extends AbilityUseEvent implements ICancellableEvent {
        public Pre(Entity entity, Identifier ability, AbilityDefinition definition, FormulaContext context) {
            super(entity, ability, definition, context);
        }
    }

    public static final class Post extends AbilityUseEvent {
        private final Map<Identifier, Double> paidCosts;

        public Post(Entity entity, Identifier ability, AbilityDefinition definition, FormulaContext context, Map<Identifier, Double> paidCosts) {
            super(entity, ability, definition, context);
            this.paidCosts = Map.copyOf(paidCosts);
        }

        public Map<Identifier, Double> paidCosts() {
            return this.paidCosts;
        }
    }
}
