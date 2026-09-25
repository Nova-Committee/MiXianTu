package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.SpiritStatsAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig.LifespanOutcome;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public abstract class LifeSpanEndEvent extends Event {
    private final Entity entity;
    private final SpiritStatsAttachment spirit;

    protected LifeSpanEndEvent(Entity entity, SpiritStatsAttachment spirit) {
        this.entity = entity;
        this.spirit = spirit;
    }

    public Entity entity() {
        return this.entity;
    }

    public SpiritStatsAttachment spirit() {
        return this.spirit;
    }

    /**
     * Cancelling is how content saves the body: a listener that writes a positive lifespan inside the event has
     * extended the life and the countdown carries on, while a listener that writes nothing lets the account
     * close. The value is read back from {@link #spirit()} after the event, so writing through the service works
     * just as well as writing on the attachment.
     */
    public static final class Pre extends LifeSpanEndEvent implements ICancellableEvent {
        public Pre(Entity entity, SpiritStatsAttachment spirit) {
            super(entity, spirit);
        }
    }

    public static final class Post extends LifeSpanEndEvent {
        private final LifespanOutcome outcome;

        public Post(Entity entity, SpiritStatsAttachment spirit, LifespanOutcome outcome) {
            super(entity, spirit);
            this.outcome = outcome;
        }

        // Which branch the base ran, so a listener can tell "nothing was done for me" from "the body was reset".
        public LifespanOutcome outcome() {
            return this.outcome;
        }
    }
}
