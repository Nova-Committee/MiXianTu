package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.TribulationAttachment;
import com.iafenvoy.mxt.data.Tribulation;
import net.minecraft.core.Holder;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Lifecycle events around the persisted tribulation phase cursor.
 */
public abstract class TribulationEvent extends Event {
    private final TribulationAttachment data;
    private final Holder<Tribulation> tribulation;
    private final int phase;

    protected TribulationEvent(@NotNull TribulationAttachment data, @NotNull Holder<Tribulation> tribulation, int phase) {
        this.data = data;
        this.tribulation = tribulation;
        this.phase = phase;
    }

    public TribulationAttachment data() {
        return this.data;
    }

    public Holder<Tribulation> tribulation() {
        return this.tribulation;
    }

    public int phase() {
        return this.phase;
    }

    public static final class StartPre extends TribulationEvent implements ICancellableEvent {
        public StartPre(TribulationAttachment data, Holder<Tribulation> tribulation) {
            super(data, tribulation, 0);
        }
    }

    public static final class StartPost extends TribulationEvent {
        public StartPost(TribulationAttachment data, Holder<Tribulation> tribulation) {
            super(data, tribulation, 0);
        }
    }

    public static final class PhasePre extends TribulationEvent implements ICancellableEvent {
        public PhasePre(TribulationAttachment data, Holder<Tribulation> tribulation, int phase) {
            super(data, tribulation, phase);
        }
    }

    public static final class PhasePost extends TribulationEvent {
        public PhasePost(TribulationAttachment data, Holder<Tribulation> tribulation, int phase) {
            super(data, tribulation, phase);
        }
    }

    public static final class Complete extends TribulationEvent {
        public Complete(TribulationAttachment data, Holder<Tribulation> tribulation, int phase) {
            super(data, tribulation, phase);
        }
    }
}
