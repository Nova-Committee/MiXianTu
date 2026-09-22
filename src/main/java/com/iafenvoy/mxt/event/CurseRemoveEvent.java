package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.CurseHolderAttachment;
import com.iafenvoy.mxt.attachment.CurseHolderAttachment.State;
import com.iafenvoy.mxt.data.curse.Curse;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Server-side curse removal transaction events, including expiry and cleansing.
 */
public abstract class CurseRemoveEvent extends Event {
    private final CurseHolderAttachment holder;
    private final Holder<Curse> curse;
    private final State state;
    private final Set<Identifier> sources;
    private final Reason reason;
    private final long gameTime;

    protected CurseRemoveEvent(@NotNull CurseHolderAttachment holder, @NotNull Holder<Curse> curse, @NotNull State state,
                               @NotNull Set<Identifier> sources, @NotNull Reason reason, long gameTime) {
        this.holder = holder;
        this.curse = curse;
        this.state = state;
        this.sources = Set.copyOf(sources);
        this.reason = reason;
        this.gameTime = gameTime;
    }

    public CurseHolderAttachment holder() {
        return this.holder;
    }

    public Holder<Curse> curse() {
        return this.curse;
    }

    public State state() {
        return this.state;
    }

    // Captured before the transaction erased them: a release that emptied the ledger reports nothing, which is
    // exactly what "no source holds it any more" means, while a whole-instance removal reports what it took away.
    public Set<Identifier> sources() {
        return this.sources;
    }

    public Reason reason() {
        return this.reason;
    }

    public long gameTime() {
        return this.gameTime;
    }

    public static final class Pre extends CurseRemoveEvent implements ICancellableEvent {
        public Pre(CurseHolderAttachment holder, Holder<Curse> curse, State state, Set<Identifier> sources, Reason reason, long gameTime) {
            super(holder, curse, state, sources, reason, gameTime);
        }
    }

    public static final class Post extends CurseRemoveEvent {
        public Post(CurseHolderAttachment holder, Holder<Curse> curse, State state, Set<Identifier> sources, Reason reason, long gameTime) {
            super(holder, curse, state, sources, reason, gameTime);
        }
    }

    // Every value has a real emitter: explicit removal, expiry, cleansing, or a replace stacking mode displacing it.
    public enum Reason {EXPLICIT, EXPIRED, CLEANSED, REPLACED}
}
