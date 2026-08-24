package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.CurseHolderAttachment;
import com.iafenvoy.mxt.attachment.CurseHolderAttachment.State;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Server-side curse removal transaction events, including expiry and cleansing.
 */
public abstract class CurseRemoveEvent extends Event {
    private final CurseHolderAttachment holder;
    private final Identifier curse;
    private final State state;
    private final Reason reason;
    private final long gameTime;

    protected CurseRemoveEvent(@NotNull CurseHolderAttachment holder, @NotNull Identifier curse, @NotNull State state, @NotNull Reason reason, long gameTime) {
        this.holder = holder;
        this.curse = curse;
        this.state = state;
        this.reason = reason;
        this.gameTime = gameTime;
    }

    public CurseHolderAttachment holder() {
        return this.holder;
    }

    public Identifier curse() {
        return this.curse;
    }

    public State state() {
        return this.state;
    }

    public Reason reason() {
        return this.reason;
    }

    public long gameTime() {
        return this.gameTime;
    }

    public static final class Pre extends CurseRemoveEvent implements ICancellableEvent {
        public Pre(CurseHolderAttachment holder, Identifier curse, State state, Reason reason, long gameTime) {
            super(holder, curse, state, reason, gameTime);
        }
    }

    public static final class Post extends CurseRemoveEvent {
        public Post(CurseHolderAttachment holder, Identifier curse, State state, Reason reason, long gameTime) {
            super(holder, curse, state, reason, gameTime);
        }
    }

    public enum Reason {EXPLICIT, EXPIRED, CLEANSED, REPLACED, CONTENT_ACTION, ADMIN}
}
