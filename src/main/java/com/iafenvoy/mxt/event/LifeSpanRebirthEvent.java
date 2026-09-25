package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.SpiritStatsAttachment;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * A rebirth somebody asked for right now, as opposed to one a spent life forced. Only the explicit entry points
 * post it; expiry has its own pair, and there a cancel could not be honoured without stranding a spent life.
 */
public abstract class LifeSpanRebirthEvent extends Event {
    private final Entity entity;
    private final SpiritStatsAttachment spirit;

    protected LifeSpanRebirthEvent(Entity entity, SpiritStatsAttachment spirit) {
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
     * Cancelling refuses the whole rebirth: nothing has happened yet, so the body is left exactly as it was. This
     * is how content protects a body it means to handle itself.
     */
    public static final class Pre extends LifeSpanRebirthEvent implements ICancellableEvent {
        public Pre(Entity entity, SpiritStatsAttachment spirit) {
            super(entity, spirit);
        }
    }

    /**
     * The reset list has run and the ledger has been reopened; {@link #spirit()} is the next life's ledger.
     */
    public static final class Post extends LifeSpanRebirthEvent {
        public Post(Entity entity, SpiritStatsAttachment spirit) {
            super(entity, spirit);
        }
    }
}
