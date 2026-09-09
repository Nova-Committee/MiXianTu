package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.CurseHolderAttachment;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.runtime.curse.CurseInstance;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Server-side curse application transaction events.
 */
public abstract class CurseApplyEvent extends Event {
    private final CurseHolderAttachment holder;
    private final Holder<Curse> curse;
    private final long gameTime;
    private final FormulaContext context;

    protected CurseApplyEvent(@NotNull CurseHolderAttachment holder, @NotNull Holder<Curse> curse, long gameTime, @NotNull FormulaContext context) {
        this.holder = holder;
        this.curse = curse;
        this.gameTime = gameTime;
        this.context = context;
    }

    public CurseHolderAttachment holder() {
        return this.holder;
    }

    public Holder<Curse> curse() {
        return this.curse;
    }

    public long gameTime() {
        return this.gameTime;
    }

    public FormulaContext context() {
        return this.context;
    }

    public static final class Pre extends CurseApplyEvent implements ICancellableEvent {
        private int stacks;
        private String source;

        public Pre(CurseHolderAttachment holder, Holder<Curse> curse, int stacks, long gameTime, FormulaContext context, String source) {
            super(holder, curse, gameTime, context);
            this.setStacks(stacks);
            this.setSource(source);
        }

        public int stacks() {
            return this.stacks;
        }

        public String source() {
            return this.source;
        }

        public void setStacks(int stacks) {
            if (stacks < 1) throw new IllegalArgumentException("Curse stacks must be positive");
            this.stacks = stacks;
        }

        public void setSource(@NotNull String source) {
            this.source = source;
        }
    }

    public static final class Post extends CurseApplyEvent {
        private final CurseInstance instance;

        public Post(CurseHolderAttachment holder, Holder<Curse> curse, long gameTime, FormulaContext context, @NotNull CurseInstance instance) {
            super(holder, curse, gameTime, context);
            this.instance = instance;
        }

        public CurseInstance instance() {
            return this.instance;
        }
    }
}
