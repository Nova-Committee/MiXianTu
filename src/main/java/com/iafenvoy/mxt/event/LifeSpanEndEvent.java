package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.LifespanAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig.LifespanOutcome;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public abstract class LifeSpanEndEvent extends Event {
    private final Entity entity;
    private final LifespanAttachment ledger;

    protected LifeSpanEndEvent(Entity entity, LifespanAttachment ledger) {
        this.entity = entity;
        this.ledger = ledger;
    }

    public Entity entity() {
        return this.entity;
    }

    public LifespanAttachment spirit() {
        return this.ledger;
    }

    /**
     * Cancelling is how content saves the body: a listener that writes a positive lifespan inside the event has
     * extended the life and the countdown carries on, while a listener that writes nothing lets the account
     * close. The value is read back from {@link #spirit()} after the event, so writing through the service works
     * just as well as writing on the attachment.
     */
    public static final class Pre extends LifeSpanEndEvent implements ICancellableEvent {
        public Pre(Entity entity, LifespanAttachment ledger) {
            super(entity, ledger);
        }
    }

    public static final class Post extends LifeSpanEndEvent {
        private final LifespanOutcome outcome;

        public Post(Entity entity, LifespanAttachment ledger, LifespanOutcome outcome) {
            super(entity, ledger);
            this.outcome = outcome;
        }

        // Which branch the base ran, so a listener can tell "nothing was done for me" from "the body was reset".
        public LifespanOutcome outcome() {
            return this.outcome;
        }
    }
}
