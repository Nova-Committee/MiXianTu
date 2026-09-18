package com.iafenvoy.mxt.runtime.spirit;

import com.iafenvoy.mxt.data.aura.Aura;
import net.minecraft.core.Holder;

import java.util.List;
import java.util.Optional;

/**
 * What one stack's store is made of, aura by aura, in the order a pour should fill it: what each aura holds now
 * and what each can hold.
 * <p>
 * How fast any of it is poured is deliberately not in here. That is a property of the gesture rather than of the
 * store - {@link SpiritChargeService} states it, and for an item the shared {@code item_aura} definition
 * describes it reads it from that definition - so a store only has to say what it is, not how it is filled.
 * <p>
 * The numbers are for the whole stack, because the item is handed its own stack and is the only thing that can
 * say what a stack of them holds - a shared {@code item_aura} definition describes one item, and whatever reads
 * that definition for an item multiplies by the stack itself.
 * <p>
 * A store may take several auras, and a pour fills one of them at a time: {@link #active} is the one it would
 * fill next, and {@link #full} is the question that decides whether there is anything to pour at all.
 */
public record SpiritPour(List<Entry> entries) {
    public SpiritPour {
        entries = List.copyOf(entries);
    }

    /**
     * One aura this store takes: what it holds and what it can hold.
     */
    public record Entry(Holder<Aura> aura, int stored, int capacity) {
        public boolean full() {
            return this.stored >= this.capacity;
        }
    }

    /**
     * A store that takes no aura at all - a carrier with nothing written on it. It is not full: there is nothing
     * to pour and nothing to fill, so it is never handed to a gesture.
     */
    public boolean empty() {
        return this.entries.isEmpty();
    }

    public boolean full() {
        return !this.entries.isEmpty() && this.entries.stream().allMatch(Entry::full);
    }

    /**
     * The aura a pour would fill next: the first one that is not full. Once every one of them is, the first is
     * answered anyway, so that a caller asking where to pour is handed the full store rather than nothing -
     * {@link #full} is the question that matters, and this is only ever a place to pour into.
     */
    public Optional<Entry> active() {
        return this.entries.stream().filter(entry -> !entry.full()).findFirst()
                .or(() -> this.entries.stream().findFirst());
    }
}
