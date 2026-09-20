package com.iafenvoy.mxt.runtime.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Persisted realm instances, kept on the overworld so a claimed realm survives a restart together with the
 * region files of its own dimension.
 */
public final class RealmWorldAttachment {
    public static final MapCodec<RealmWorldAttachment> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RealmRecord.CODEC.listOf().optionalFieldOf("instances", List.of()).forGetter(RealmWorldAttachment::records)
    ).apply(i, RealmWorldAttachment::new));
    private final List<RealmRecord> records;

    public RealmWorldAttachment() {
        this(List.of());
    }

    private RealmWorldAttachment(List<RealmRecord> records) {
        this.records = new ArrayList<>(records);
    }

    public List<RealmRecord> records() {
        return this.records;
    }

    public void replaceAll(List<RealmRecord> value) {
        this.records.clear();
        this.records.addAll(value);
    }
}
