package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.TechniqueAttachment;
import com.iafenvoy.mxt.data.cultivation.Technique;
import net.minecraft.core.Holder;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public abstract class TechniqueLearnEvent extends Event {
    private final TechniqueAttachment spirit;
    private final Holder<Technique> technique;

    protected TechniqueLearnEvent(TechniqueAttachment spirit, Holder<Technique> technique) {
        this.spirit = spirit.copy();
        this.technique = technique;
    }

    /**
     * Returns a detached snapshot of the state at publication.
     */
    public TechniqueAttachment getSpirit() {
        return this.spirit.copy();
    }

    public Holder<Technique> getTechnique() {
        return this.technique;
    }

    public static final class Pre extends TechniqueLearnEvent implements ICancellableEvent {
        public Pre(TechniqueAttachment spirit, Holder<Technique> technique) {
            super(spirit, technique);
        }
    }

    public static final class Post extends TechniqueLearnEvent {
        public Post(TechniqueAttachment spirit, Holder<Technique> technique) {
            super(spirit, technique);
        }
    }
}
