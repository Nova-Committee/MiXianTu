package com.iafenvoy.mxt.runtime.spirit;

import com.iafenvoy.mxt.data.aura.Aura;
import net.minecraft.core.Holder;

import java.util.List;
import java.util.Optional;

/**
 * What one stack's store is made of, aura by aura, in the order a pour fills it.
 * <p>
 * The pour rate is deliberately absent: it belongs to the gesture, not to the store. The numbers cover the
 * whole stack, since only the item can say what a stack of them holds.
 */
public record SpiritPour(List<Entry> entries) {
    public SpiritPour {
        entries = List.copyOf(entries);
    }

    public record Entry(Holder<Aura> aura, int stored, int capacity) {
        public boolean full() {
            return this.stored >= this.capacity;
        }
    }

    // A store taking no aura at all - a carrier with nothing written on it - is not full, and is never handed
    // to a gesture.
    public boolean empty() {
        return this.entries.isEmpty();
    }

    public boolean full() {
        return !this.entries.isEmpty() && this.entries.stream().allMatch(Entry::full);
    }

    // Once every entry is full the first is answered anyway, so a caller asking where to pour is handed the
    // full store rather than nothing.
    public Optional<Entry> active() {
        return this.entries.stream().filter(entry -> !entry.full()).findFirst()
                .or(() -> this.entries.stream().findFirst());
    }
}
