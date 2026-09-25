package com.iafenvoy.mxt.data.aura;

import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMaps;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.core.Holder;

import java.util.Optional;

/**
 * Persistent store on an item stack that implements {@code ItemAuraAccess}: how much of which auras the stack
 * holds. Amounts are doubles, so an artifact's fuel can be bought a fraction a tick while a talisman is still
 * filled one whole unit at a time; a pour only ever writes whole units. The key is an {@link Aura}, never the
 * {@code resource} it is counted in - a plain counter carries no aura and has no business here. A missing
 * component means whatever the item says (pristine stone is full, untouched carrier is empty); an empty map is a
 * third thing, a store that was drained.
 */
public record SpiritStorageComponent(Object2DoubleMap<Holder<Aura>> amounts) {
    public static final SpiritStorageComponent EMPTY = new SpiritStorageComponent(Object2DoubleMaps.emptyMap());
    public static final Codec<SpiritStorageComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            CollectionCodecs.doubleMap(Aura.CODEC).optionalFieldOf("amounts", Object2DoubleMaps.emptyMap())
                    .forGetter(SpiritStorageComponent::amounts)
    ).apply(i, SpiritStorageComponent::new));

    public SpiritStorageComponent {
        Object2DoubleMap<Holder<Aura>> kept = new Object2DoubleOpenHashMap<>();
        amounts.forEach((aura, amount) -> {
            if (aura == null) throw new IllegalArgumentException("A stored amount must name an aura");
            if (!Double.isFinite(amount) || amount < 0.0D)
                throw new IllegalArgumentException("Stored amounts must be finite and not negative");
            // An aura with nothing in it is not stored at all, so that an emptied store and a store that was
            // never written to are told apart by the component's presence rather than by a zero inside it.
            if (amount > 0.0D) kept.put(aura, amount);
        });
        amounts = kept;
    }

    public double get(Holder<Aura> aura) {
        return this.amounts.getDouble(aura);
    }

    public boolean isEmpty() {
        return this.amounts.isEmpty();
    }

    public SpiritStorageComponent with(Holder<Aura> aura, double amount) {
        if (!Double.isFinite(amount) || amount < 0.0D)
            throw new IllegalArgumentException("Stored amounts must be finite and not negative");
        Object2DoubleMap<Holder<Aura>> next = new Object2DoubleOpenHashMap<>(this.amounts);
        if (amount == 0.0D) next.removeDouble(aura);
        else next.put(aura, amount);
        return new SpiritStorageComponent(next);
    }

    // Empty when the store holds nothing or names several auras; an item that stores several answers for itself.
    public Optional<Holder<Aura>> soleAura() {
        return this.amounts.size() == 1 ? Optional.of(this.amounts.keySet().iterator().next()) : Optional.empty();
    }
}
