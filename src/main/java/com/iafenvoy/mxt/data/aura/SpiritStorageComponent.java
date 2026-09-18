package com.iafenvoy.mxt.data.aura;

import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.Holder;

import java.util.Optional;

/**
 * Persistent store written into an item stack that implements {@code ItemAuraAccess}: which auras the stack
 * holds, and how much of each.
 * <p>
 * The key is an {@link Aura} rather than the {@code resource} it is counted in, because what is stored is
 * aura and the aura is what says which one it is; the value it is measured in is a field of that aura
 * ({@code Aura#resource}) and never a second identity. A plain counter carries no aura and therefore has no
 * business in here - a store of whole units is a store of aura.
 * <p>
 * One shape serves every whole-unit store. A spirit stone holds a single aura and a talisman carrier is
 * billed in as many as its inscriptions name, but both are the same question - how many units of which aura
 * are in this stack - and filing the amount under the aura it is counted in is what answers it. That filing
 * is also what keeps a data pack from reinterpreting a store: an amount cannot change meaning when its key is
 * part of it, so re-typing an {@code item_aura} definition can only stop matching what is already in the
 * world.
 * <p>
 * What a missing component means is the item's answer rather than this record's: a stone that has never been
 * touched is pristine and therefore full, while a carrier that has never been poured into is empty and inert.
 * An empty map is a third thing - a store that was drained - and only the item can say whether it still counts
 * as full.
 */
public record SpiritStorageComponent(Object2IntMap<Holder<Aura>> amounts) {
    public static final SpiritStorageComponent EMPTY = new SpiritStorageComponent(Object2IntMaps.emptyMap());
    public static final Codec<SpiritStorageComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            CollectionCodecs.intMap(Aura.CODEC).optionalFieldOf("amounts", Object2IntMaps.emptyMap())
                    .forGetter(SpiritStorageComponent::amounts)
    ).apply(i, SpiritStorageComponent::new));

    public SpiritStorageComponent {
        Object2IntMap<Holder<Aura>> kept = new Object2IntOpenHashMap<>();
        amounts.forEach((aura, amount) -> {
            if (aura == null) throw new IllegalArgumentException("A stored amount must name an aura");
            if (amount < 0) throw new IllegalArgumentException("Stored amounts must not be negative");
            // An aura with nothing in it is not stored at all, so that an emptied store and a store that was
            // never written to are told apart by the component's presence rather than by a zero inside it.
            if (amount > 0) kept.put(aura, amount);
        });
        amounts = kept;
    }

    public int get(Holder<Aura> aura) {
        return this.amounts.getInt(aura);
    }

    public boolean isEmpty() {
        return this.amounts.isEmpty();
    }

    public SpiritStorageComponent with(Holder<Aura> aura, int amount) {
        if (amount < 0) throw new IllegalArgumentException("Stored amounts must not be negative");
        Object2IntMap<Holder<Aura>> next = new Object2IntOpenHashMap<>(this.amounts);
        if (amount == 0) next.removeInt(aura);
        else next.put(aura, amount);
        return new SpiritStorageComponent(next);
    }

    /**
     * The one aura this store holds, when it holds exactly one. An item that stores a single aura - a spirit
     * stone - reads what is in it from this, so that a store written under one aura is never answered for
     * another; an item that stores several answers for itself instead ({@code UseItemAuraAccess#pour}).
     * <p>
     * Empty when the store holds nothing or names several auras, and the caller then has only its own
     * declaration to go on - which is exactly right: a drained stone is empty of what it declares, and a store
     * naming several is not a state anything in this mod writes.
     */
    public Optional<Holder<Aura>> soleAura() {
        return this.amounts.size() == 1 ? Optional.of(this.amounts.keySet().iterator().next()) : Optional.empty();
    }
}
