package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.SpiritData;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public abstract class LifeSpanEndEvent extends Event {
    private final Entity entity;
    private final SpiritData spirit;

    protected LifeSpanEndEvent(Entity entity, SpiritData spirit) {
        this.entity = entity;
        this.spirit = spirit;
    }

    public Entity entity() {
        return this.entity;
    }

    public SpiritData spirit() {
        return this.spirit;
    }

    public static final class Pre extends LifeSpanEndEvent implements ICancellableEvent {
        public Pre(Entity entity, SpiritData spirit) {
            super(entity, spirit);
        }
    }

    public static final class Post extends LifeSpanEndEvent {
        public Post(Entity entity, SpiritData spirit) {
            super(entity, spirit);
        }
    }
}
