package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * "These stacks are not finished yet", declared by a data pack: nothing but a {@link ItemMatcher}, because the mark
 * belongs to the pack rather than to an item class, and a declaration is free to name an item, a tag, another mod's
 * item, or a reading that needs the stack (a tier, an ingredient).
 *
 * <p>Overlap is deliberately not resolved: every matching declaration draws the same badge, so order, and therefore
 * {@link #priority()}, stays at the interface default and no file has to be kept ahead of another.
 */
public record IncompleteMarker(List<Entry> entries) implements ItemMatcher {
    public static final Codec<IncompleteMarker> CODEC = RecordCodecBuilder.<IncompleteMarker>create(i -> i.group(
            ENTRIES_CODEC.fieldOf("items").forGetter(IncompleteMarker::entries)
    ).apply(i, IncompleteMarker::new)).validate(IncompleteMarker::validate);

    // Deliberately not a field: there is nothing to order when every matching declaration draws the same badge.
    @Override
    public int priority() {
        return DEFAULT_PRIORITY;
    }

    // The matcher's reading is "any one entry matching", so an empty list marks nothing at all: it can only be a
    // file written against a list that was still meant to be filled in.
    private static DataResult<IncompleteMarker> validate(IncompleteMarker marker) {
        return marker.entries().isEmpty()
                ? DataResult.error(() -> "An incomplete declaration needs at least one item entry")
                : DataResult.success(marker);
    }
}
