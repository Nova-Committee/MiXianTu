package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.SpiritAttachment;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public abstract class LifeSpanEndEvent extends Event {
    private final Entity entity;
    private final SpiritAttachment spirit;

    protected LifeSpanEndEvent(Entity entity, SpiritAttachment spirit) {
        this.entity = entity;
        this.spirit = spirit;
    }

    public Entity entity() {
        return this.entity;
    }

    public SpiritAttachment spirit() {
        return this.spirit;
    }

    public static final class Pre extends LifeSpanEndEvent implements ICancellableEvent {
        public Pre(Entity entity, SpiritAttachment spirit) {
            super(entity, spirit);
        }
    }

    public static final class Post extends LifeSpanEndEvent {
        public Post(Entity entity, SpiritAttachment spirit) {
            super(entity, spirit);
        }
    }
}
