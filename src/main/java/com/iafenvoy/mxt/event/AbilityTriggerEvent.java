package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;

/**
 * Emitted after an original game event selects a triggered ability and before it is used.
 */
public abstract class AbilityTriggerEvent extends EntityEvent {
    private final Identifier ability;
    private final Ability definition;
    private final String trigger;
    private final FormulaContext context;

    protected AbilityTriggerEvent(Entity entity, Identifier ability, Ability definition, String trigger, FormulaContext context) {
        super(entity);
        this.ability = ability;
        this.definition = definition;
        this.trigger = trigger;
        this.context = context;
    }

    public Identifier ability() {
        return this.ability;
    }

    public Ability definition() {
        return this.definition;
    }

    public String trigger() {
        return this.trigger;
    }

    public FormulaContext context() {
        return this.context;
    }

    public static final class Pre extends AbilityTriggerEvent implements ICancellableEvent {
        public Pre(Entity entity, Identifier ability, Ability definition, String trigger, FormulaContext context) {
            super(entity, ability, definition, trigger, context);
        }
    }

    public static final class Post extends AbilityTriggerEvent {
        public Post(Entity entity, Identifier ability, Ability definition, String trigger, FormulaContext context) {
            super(entity, ability, definition, trigger, context);
        }
    }
}
