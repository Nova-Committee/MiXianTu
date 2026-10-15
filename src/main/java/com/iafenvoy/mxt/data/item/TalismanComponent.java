package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.talisman.Talisman;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The talismans written onto one carrier, in append order, and how this carrier answers being filled. Writing a
 * talisman appends rather than replaces, so one carrier can hold several; an empty list is a blank carrier. The
 * mode lives on the stack, not on any definition, and {@link TriggerMode#FIRE} is the default - which is also
 * what every carrier written before this field existed decodes as. What this component reads as on a tooltip is
 * written by {@code TalismanTooltipAppender}, not here: those lines have to sit above the carrier's charge line.
 */
public record TalismanComponent(List<Holder<Talisman>> talismans, TriggerMode mode) {
    public static final TalismanComponent EMPTY = new TalismanComponent(List.of(), TriggerMode.FIRE);
    public static final Codec<TalismanComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Talisman.CODEC.listOf().optionalFieldOf("talismans", List.of()).forGetter(TalismanComponent::talismans),
            TriggerMode.CODEC.optionalFieldOf("mode", TriggerMode.FIRE).forGetter(TalismanComponent::mode)
    ).apply(i, TalismanComponent::new));

    // FIRE fires the moment the bill is paid, which is what makes a carrier a one-shot; STORE only accumulates
    // and waits to be fired by a hand or switched back.
    public enum TriggerMode implements StringRepresentable {
        FIRE("fire"),
        STORE("store");
        public static final Codec<TriggerMode> CODEC = StringRepresentable.fromValues(TriggerMode::values);

        private final String key;

        TriggerMode(String key) {
            this.key = key;
        }

        public String key() {
            return this.key;
        }

        public TriggerMode other() {
            return this == FIRE ? STORE : FIRE;
        }

        @Override
        public @NonNull String getSerializedName() {
            return this.key;
        }
    }

    // The appended definition is kept as written: a carrier may hold the same talisman twice, so "write it again"
    // is a no-op the crafting loop decides on rather than a rule hidden here.
    public TalismanComponent appended(Holder<Talisman> talisman) {
        List<Holder<Talisman>> appended = new ArrayList<>(this.talismans);
        appended.add(talisman);
        return new TalismanComponent(List.copyOf(appended), this.mode);
    }

    public TalismanComponent withMode(TriggerMode mode) {
        return new TalismanComponent(this.talismans, mode);
    }
}
