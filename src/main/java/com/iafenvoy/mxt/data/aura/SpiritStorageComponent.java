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
 * Persistent store on an item stack that implements {@code ItemAuraAccess}: how many whole units of which auras
 * the stack holds. The key is an {@link Aura}, never the {@code resource} it is counted in - a plain counter
 * carries no aura and has no business here. A missing component means whatever the item says (pristine stone is
 * full, untouched carrier is empty); an empty map is a third thing, a store that was drained.
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

    // Empty when the store holds nothing or names several auras; an item that stores several answers for itself.
    public Optional<Holder<Aura>> soleAura() {
        return this.amounts.size() == 1 ? Optional.of(this.amounts.keySet().iterator().next()) : Optional.empty();
    }
}
