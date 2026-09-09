package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.cultivation.CultivationTechnique;
import net.minecraft.core.Holder;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public abstract class TechniqueLearnEvent extends Event {
    private final SpiritIdentityAttachment spirit;
    private final Holder<CultivationTechnique> technique;

    protected TechniqueLearnEvent(SpiritIdentityAttachment spirit, Holder<CultivationTechnique> technique) {
        this.spirit = spirit;
        this.technique = technique;
    }

    public SpiritIdentityAttachment spirit() {
        return this.spirit;
    }

    public Holder<CultivationTechnique> technique() {
        return this.technique;
    }

    public static final class Pre extends TechniqueLearnEvent implements ICancellableEvent {
        public Pre(SpiritIdentityAttachment spirit, Holder<CultivationTechnique> technique) {
            super(spirit, technique);
        }
    }

    public static final class Post extends TechniqueLearnEvent {
        public Post(SpiritIdentityAttachment spirit, Holder<CultivationTechnique> technique) {
            super(spirit, technique);
        }
    }
}
