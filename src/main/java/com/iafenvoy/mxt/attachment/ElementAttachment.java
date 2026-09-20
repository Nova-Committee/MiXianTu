package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How much of each element has built up on one entity.
 *
 * <p>This is the storage half of the element accumulation system: a number per element, no opinions. What
 * builds it up is a strike of that element ({@code damage_attachment}) or anything that applies it directly;
 * what it does when it is full enough is a {@code mxt:element_reaction}; what wears it off is
 * {@code attachment_decay}. Keeping the three apart is what lets a pack change one without touching the
 * others, and it is also why this attachment holds no rules of its own.</p>
 *
 * <p>An amount that reaches zero is dropped rather than stored, so "nothing has built up" has one
 * representation and an entity that has never been touched by an element carries an empty map.</p>
 */
public final class ElementAttachment extends ShouldSyncAttachment {
    public static final MapCodec<ElementAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.map(Element.CODEC, Codec.DOUBLE).optionalFieldOf("amounts", Map.of()).forGetter(ElementAttachment::amounts)
    ).apply(i, ElementAttachment::new));
    private final Map<Holder<Element>, Double> amounts;

    public ElementAttachment() {
        this(Map.of());
    }

    private ElementAttachment(Map<Holder<Element>, Double> amounts) {
        this.amounts = new LinkedHashMap<>();
        amounts.forEach(this::put);
    }

    public Map<Holder<Element>, Double> amounts() {
        return Map.copyOf(this.amounts);
    }

    public double amount(Holder<Element> element) {
        return this.amounts.getOrDefault(element, 0.0D);
    }

    public boolean isEmpty() {
        return this.amounts.isEmpty();
    }

    public void set(Holder<Element> element, double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Element attachment must be finite");
        this.markDirty();
        if (value <= 0.0D) this.amounts.remove(element);
        else this.amounts.put(element, value);
    }

    /**
     * Adds to one element's buildup and answers the new total. A negative amount takes away, which is how a
     * cleanse is written; the floor is zero because a negative buildup has no meaning.
     */
    public double add(Holder<Element> element, double delta) {
        double total = Math.max(0.0D, this.amount(element) + delta);
        this.set(element, total);
        return total;
    }

    public void clear() {
        if (this.amounts.isEmpty()) return;
        this.amounts.clear();
        this.markDirty();
    }

    private void put(Holder<Element> element, double value) {
        if (Double.isFinite(value) && value > 0.0D) this.amounts.put(element, value);
    }
}
