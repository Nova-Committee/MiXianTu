package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.SoulComponent;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public abstract class SoulEvent extends Event {
    private final Entity entity;
    private final SoulComponent soul;

    protected SoulEvent(Entity entity, SoulComponent soul) {
        this.entity = entity;
        this.soul = soul;
    }

    public Entity entity() {
        return this.entity;
    }

    public SoulComponent soul() {
        return this.soul;
    }

    public static final class TransferPre extends SoulEvent implements ICancellableEvent {
        public TransferPre(Entity entity, SoulComponent soul) {
            super(entity, soul);
        }
    }

    public static final class TransferPost extends SoulEvent {
        public TransferPost(Entity entity, SoulComponent soul) {
            super(entity, soul);
        }
    }

    /**
     * Fired by an explicit rescue or resurrection integration before soul state is cleared.
     */
    public static final class ReclaimPre extends SoulEvent implements ICancellableEvent {
        public ReclaimPre(Entity entity, SoulComponent soul) {
            super(entity, soul);
        }
    }

    public static final class ReclaimPost extends SoulEvent {
        public ReclaimPost(Entity entity, SoulComponent soul) {
            super(entity, soul);
        }
    }
}
