package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-authoritative slot order for configurable client hotbar modes.
 */
public final class HotbarLayoutAttachment extends ShouldSyncAttachment {
    public static final Identifier EMPTY_SLOT = Identifier.fromNamespaceAndPath("mxt", "empty_hotbar_slot");
    public static final MapCodec<HotbarLayoutAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.map(Identifier.CODEC, Identifier.CODEC.listOf()).optionalFieldOf("layouts", Map.of()).forGetter(HotbarLayoutAttachment::layouts)
    ).apply(i, HotbarLayoutAttachment::new));
    private final Map<Identifier, List<Identifier>> layouts;

    public HotbarLayoutAttachment() {
        this(Map.of());
    }

    private HotbarLayoutAttachment(Map<Identifier, List<Identifier>> layouts) {
        this.layouts = new LinkedHashMap<>();
        layouts.forEach((mode, slots) -> this.layouts.put(mode, new ArrayList<>(slots.subList(0, Math.min(9, slots.size())))));
    }

    public Map<Identifier, List<Identifier>> layouts() {
        return this.layouts;
    }

    public List<Identifier> slots(Identifier mode) {
        return this.layouts.getOrDefault(mode, List.of());
    }

    public void setSlots(Identifier mode, List<Identifier> slots) {
        if (mode == null) return;
        List<Identifier> copy = new ArrayList<>(Math.min(9, slots.size()));
        slots.stream().limit(9).map(id -> id == null ? EMPTY_SLOT : id).forEach(copy::add);
        // Keep an all-empty nine-slot layout: it is an explicit user choice,
        // distinct from a mode that has never been configured.
        this.layouts.put(mode, copy);
        this.markDirty();
    }
}
