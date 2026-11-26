package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.CurseHolderAttachment;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.runtime.curse.CurseInstance;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;

/**
 * Server-side curse application transaction events.
 */
public abstract class CurseApplyEvent extends Event {
    private final CurseHolderAttachment holder;
    private final Holder<Curse> curse;
    private final long gameTime;
    private final FormulaContext context;

    protected CurseApplyEvent(@NotNull CurseHolderAttachment holder, @NotNull Holder<Curse> curse, long gameTime, @NotNull FormulaContext context) {
        this.holder = holder.copy();
        this.curse = curse;
        this.gameTime = gameTime;
        this.context = context;
    }

    /** Returns a detached snapshot of the state at publication. */
    public CurseHolderAttachment getHolder() {
        return this.holder.copy();
    }

    public Holder<Curse> getCurse() {
        return this.curse;
    }

    public long getGameTime() {
        return this.gameTime;
    }

    public FormulaContext getContext() {
        return this.context;
    }

    public static final class Pre extends CurseApplyEvent implements ICancellableEvent {
        private int stacks;
        private Identifier source;

        public Pre(CurseHolderAttachment holder, Holder<Curse> curse, int stacks, long gameTime, FormulaContext context, Identifier source) {
            super(holder, curse, gameTime, context);
            this.setStacks(stacks);
            this.setSource(source);
        }

        public int getStacks() {
            return this.stacks;
        }

        // A listener may change it, which decides which source the new instance is credited to.
        public Identifier getSource() {
            return this.source;
        }

        public void setStacks(int stacks) {
            if (stacks < 1) throw new IllegalArgumentException("Curse stacks must be positive");
            this.stacks = stacks;
        }

        public void setSource(@NotNull Identifier source) {
            this.source = Objects.requireNonNull(source, "Curse source");
        }
    }

    public static final class Post extends CurseApplyEvent {
        private final CurseInstance instance;
        private final Optional<Identifier> source;

        public Post(CurseHolderAttachment holder, Holder<Curse> curse, long gameTime, FormulaContext context, @NotNull CurseInstance instance) {
            this(holder, curse, gameTime, context, instance, null);
        }

        public Post(CurseHolderAttachment holder, Holder<Curse> curse, long gameTime, FormulaContext context,
                    @NotNull CurseInstance instance, Identifier source) {
            super(holder, curse, gameTime, context);
            this.instance = instance;
            this.source = Optional.ofNullable(source);
        }

        public CurseInstance getInstance() {
            return this.instance;
        }

        public Optional<Identifier> getSource() {
            return this.source;
        }
    }
}
