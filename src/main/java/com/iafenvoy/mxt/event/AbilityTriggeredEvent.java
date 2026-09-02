package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.trigger.TriggerContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;

/**
 * Emitted when a Trigger selects an ability, before and after that ability is used.
 */
public abstract class AbilityTriggeredEvent extends EntityEvent {
    private final Holder<Ability> ability;
    private final Identifier signalType;
    private final TriggerContext context;

    protected AbilityTriggeredEvent(Entity entity, Holder<Ability> ability, Identifier signalType, TriggerContext context) {
        super(entity);
        this.ability = ability;
        this.signalType = signalType;
        this.context = context;
    }

    public Holder<Ability> ability() {
        return this.ability;
    }

    public Identifier signalType() {
        return this.signalType;
    }

    public TriggerContext context() {
        return this.context;
    }

    public static final class Pre extends AbilityTriggeredEvent implements ICancellableEvent {
        public Pre(Entity entity, Holder<Ability> ability, Identifier signalType, TriggerContext context) {
            super(entity, ability, signalType, context);
        }
    }

    public static final class Post extends AbilityTriggeredEvent {
        public Post(Entity entity, Holder<Ability> ability, Identifier signalType, TriggerContext context) {
            super(entity, ability, signalType, context);
        }
    }
}
