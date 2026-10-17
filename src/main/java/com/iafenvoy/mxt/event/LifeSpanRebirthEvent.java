package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.LifespanAttachment;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;

/**
 * A rebirth somebody asked for right now, as opposed to one a spent life forced. Only the explicit entry points
 * post it; expiry has its own pair, and there a cancel could not be honoured without stranding a spent life.
 */
public abstract class LifeSpanRebirthEvent extends EntityEvent {
    private final LifespanAttachment ledger;

    protected LifeSpanRebirthEvent(Entity entity, LifespanAttachment ledger) {
        super(entity);
        this.ledger = ledger;
    }

    public LifespanAttachment getSpirit() {
        return this.ledger;
    }

    /**
     * Cancelling refuses the whole rebirth: nothing has happened yet, so the body is left exactly as it was. This
     * is how content protects a body it means to handle itself.
     */
    public static final class Pre extends LifeSpanRebirthEvent implements ICancellableEvent {
        public Pre(Entity entity, LifespanAttachment ledger) {
            super(entity, ledger);
        }
    }

    /**
     * The reset list has run and the ledger has been reopened; {@link #getSpirit()} is the next life's ledger.
     */
    public static final class Post extends LifeSpanRebirthEvent {
        public Post(Entity entity, LifespanAttachment ledger) {
            super(entity, ledger);
        }
    }
}
