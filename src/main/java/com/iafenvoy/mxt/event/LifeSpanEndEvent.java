package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.SpiritComponent;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public abstract class LifeSpanEndEvent extends Event {
    private final Entity entity;
    private final SpiritComponent spirit;

    protected LifeSpanEndEvent(Entity entity, SpiritComponent spirit) {
        this.entity = entity;
        this.spirit = spirit;
    }

    public Entity entity() {
        return this.entity;
    }

    public SpiritComponent spirit() {
        return this.spirit;
    }

    public static final class Pre extends LifeSpanEndEvent implements ICancellableEvent {
        public Pre(Entity entity, SpiritComponent spirit) {
            super(entity, spirit);
        }
    }

    public static final class Post extends LifeSpanEndEvent {
        public Post(Entity entity, SpiritComponent spirit) {
            super(entity, spirit);
        }
    }
}
