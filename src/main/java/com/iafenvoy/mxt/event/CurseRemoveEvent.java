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

    /**
     * Who was keeping it alive when it left, captured before the transaction erased them. A release that emptied
     * the ledger therefore reports nothing, which is exactly what "no source holds it any more" means, while a
     * whole-instance removal reports the sources it took away from.
     */
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

    /**
     * Why an instance left the holder. Every value here has a real emitter: a script or an ability removes it
     * explicitly, the scheduler expires it, a cure cleanses it, or a {@code replace} stacking mode displaces it.
     */
    public enum Reason {EXPLICIT, EXPIRED, CLEANSED, REPLACED}
}
