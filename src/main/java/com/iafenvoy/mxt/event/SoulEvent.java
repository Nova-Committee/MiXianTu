package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.SoulAttachment;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;

import java.util.Optional;
import java.util.UUID;

public abstract class SoulEvent extends EntityEvent {
    private final SoulAttachment soul;

    protected SoulEvent(Entity entity, SoulAttachment soul) {
        super(entity);
        this.soul = soul.copy();
    }

    /** Returns a detached snapshot of the state at publication. */
    public SoulAttachment getSoul() {
        return this.soul.copy();
    }

    public State getState() {
        return new State(this.soul.active(), this.soul.origin(), this.soul.createdAt(), this.soul.source(),
                this.soul.manifestation());
    }

    public record State(boolean active, String origin, long createdAt, String source, Optional<UUID> manifestation) {
    }

    public static final class TransferPre extends SoulEvent implements ICancellableEvent {
        public TransferPre(Entity entity, SoulAttachment soul) {
            super(entity, soul);
        }
    }

    public static final class TransferPost extends SoulEvent {
        public TransferPost(Entity entity, SoulAttachment soul) {
            super(entity, soul);
        }
    }

    // Fired by an explicit rescue or resurrection integration before soul state is cleared.
    public static final class ReclaimPre extends SoulEvent implements ICancellableEvent {
        public ReclaimPre(Entity entity, SoulAttachment soul) {
            super(entity, soul);
        }
    }

    public static final class ReclaimPost extends SoulEvent {
        public ReclaimPost(Entity entity, SoulAttachment soul) {
            super(entity, soul);
        }
    }
}
