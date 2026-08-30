package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.cultivation.CultivationTechnique;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public abstract class TechniqueLearnEvent extends Event {
    private final SpiritIdentityAttachment spirit;
    private final Identifier technique;
    private final CultivationTechnique definition;

    protected TechniqueLearnEvent(SpiritIdentityAttachment spirit, Identifier technique, CultivationTechnique definition) {
        this.spirit = spirit;
        this.technique = technique;
        this.definition = definition;
    }

    public SpiritIdentityAttachment spirit() {
        return this.spirit;
    }

    public Identifier technique() {
        return this.technique;
    }

    public CultivationTechnique definition() {
        return this.definition;
    }

    public static final class Pre extends TechniqueLearnEvent implements ICancellableEvent {
        public Pre(SpiritIdentityAttachment spirit, Identifier technique, CultivationTechnique definition) {
            super(spirit, technique, definition);
        }
    }

    public static final class Post extends TechniqueLearnEvent {
        public Post(SpiritIdentityAttachment spirit, Identifier technique, CultivationTechnique definition) {
            super(spirit, technique, definition);
        }
    }
}
