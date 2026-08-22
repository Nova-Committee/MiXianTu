package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.CurseHolderComponent;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.runtime.curse.CurseInstance;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Server-side curse application transaction events.
 */
public abstract class CurseApplyEvent extends Event {
    private final CurseHolderComponent holder;
    private final Identifier curse;
    private final Curse definition;
    private final long gameTime;
    private final FormulaContext context;

    protected CurseApplyEvent(@NotNull CurseHolderComponent holder, @NotNull Identifier curse, @NotNull Curse definition, long gameTime, @NotNull FormulaContext context) {
        this.holder = holder;
        this.curse = curse;
        this.definition = definition;
        this.gameTime = gameTime;
        this.context = context;
    }

    public CurseHolderComponent holder() {
        return this.holder;
    }

    public Identifier curse() {
        return this.curse;
    }

    public Curse definition() {
        return this.definition;
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

        public Pre(CurseHolderComponent holder, Identifier curse, Curse definition, int stacks, long gameTime, FormulaContext context, String source) {
            super(holder, curse, definition, gameTime, context);
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

        public Post(CurseHolderComponent holder, Identifier curse, Curse definition, long gameTime, FormulaContext context, @NotNull CurseInstance instance) {
            super(holder, curse, definition, gameTime, context);
            this.instance = instance;
        }

        public CurseInstance instance() {
            return this.instance;
        }
    }
}
