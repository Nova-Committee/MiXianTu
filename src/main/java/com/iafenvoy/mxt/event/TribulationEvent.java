package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.attachment.TribulationAttachment;
import com.iafenvoy.mxt.data.Tribulation;
import com.iafenvoy.mxt.data.timeline.TimelineEntry;
import net.minecraft.core.Holder;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Lifecycle events around the persisted tribulation timeline cursor.
 */
public abstract class TribulationEvent extends Event {
    private final TribulationAttachment data;
    private final Holder<Tribulation> tribulation;

    protected TribulationEvent(@NotNull TribulationAttachment data, @NotNull Holder<Tribulation> tribulation) {
        this.data = data;
        this.tribulation = tribulation;
    }

    public TribulationAttachment data() {
        return this.data;
    }

    public Holder<Tribulation> tribulation() {
        return this.tribulation;
    }

    // Cancelling refuses the attempt and leaves nothing behind.
    public static final class StartPre extends TribulationEvent implements ICancellableEvent {
        public StartPre(TribulationAttachment data, Holder<Tribulation> tribulation) {
            super(data, tribulation);
        }
    }

    public static final class StartPost extends TribulationEvent {
        public StartPost(TribulationAttachment data, Holder<Tribulation> tribulation) {
            super(data, tribulation);
        }
    }

    public abstract static class EntryEvent extends TribulationEvent {
        private final int index;
        private final TimelineEntry entry;

        protected EntryEvent(@NotNull TribulationAttachment data, @NotNull Holder<Tribulation> tribulation, int index,
                             @NotNull TimelineEntry entry) {
            super(data, tribulation);
            this.index = index;
            this.entry = entry;
        }

        public int index() {
            return this.index;
        }

        public TimelineEntry entry() {
            return this.entry;
        }
    }

    // Cancelling skips that entry, so a listener can drop a beat without stalling the run.
    public static final class EntryPre extends EntryEvent implements ICancellableEvent {
        public EntryPre(TribulationAttachment data, Holder<Tribulation> tribulation, int index, TimelineEntry entry) {
            super(data, tribulation, index, entry);
        }
    }

    public static final class EntryPost extends EntryEvent {
        public EntryPost(TribulationAttachment data, Holder<Tribulation> tribulation, int index, TimelineEntry entry) {
            super(data, tribulation, index, entry);
        }
    }

    public static final class Complete extends TribulationEvent {
        public Complete(TribulationAttachment data, Holder<Tribulation> tribulation) {
            super(data, tribulation);
        }
    }
}
