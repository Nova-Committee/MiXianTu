package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.TribulationData;
import com.iafenvoy.mxt.data.tribulation.TribulationDefinition;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Lifecycle events around the persisted tribulation phase cursor.
 */
public abstract class TribulationEvent extends Event {
    private final TribulationData data;
    private final Identifier tribulation;
    private final TribulationDefinition definition;
    private final int phase;

    protected TribulationEvent(@NotNull TribulationData data, @NotNull Identifier tribulation, @NotNull TribulationDefinition definition, int phase) {
        this.data = data;
        this.tribulation = tribulation;
        this.definition = definition;
        this.phase = phase;
    }

    public TribulationData data() {
        return this.data;
    }

    public Identifier tribulation() {
        return this.tribulation;
    }

    public TribulationDefinition definition() {
        return this.definition;
    }

    public int phase() {
        return this.phase;
    }

    public static final class StartPre extends TribulationEvent implements ICancellableEvent {
        public StartPre(TribulationData d, Identifier i, TribulationDefinition f) {
            super(d, i, f, 0);
        }
    }

    public static final class StartPost extends TribulationEvent {
        public StartPost(TribulationData d, Identifier i, TribulationDefinition f) {
            super(d, i, f, 0);
        }
    }

    public static final class PhasePre extends TribulationEvent implements ICancellableEvent {
        public PhasePre(TribulationData d, Identifier i, TribulationDefinition f, int p) {
            super(d, i, f, p);
        }
    }

    public static final class PhasePost extends TribulationEvent {
        public PhasePost(TribulationData d, Identifier i, TribulationDefinition f, int p) {
            super(d, i, f, p);
        }
    }

    public static final class Complete extends TribulationEvent {
        public Complete(TribulationData d, Identifier i, TribulationDefinition f, int p) {
            super(d, i, f, p);
        }
    }
}
