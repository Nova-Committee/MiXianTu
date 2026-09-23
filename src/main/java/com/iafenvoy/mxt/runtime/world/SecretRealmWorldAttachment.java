package com.iafenvoy.mxt.runtime.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Persisted secret realms, kept on the overworld so a claimed secret realm survives a restart together with the
 * region files of its own dimension.
 */
public final class SecretRealmWorldAttachment {
    public static final MapCodec<SecretRealmWorldAttachment> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SecretRealmRecord.CODEC.listOf().lenientOptionalFieldOf("instances", List.of()).forGetter(SecretRealmWorldAttachment::records)
    ).apply(i, SecretRealmWorldAttachment::new));
    private final List<SecretRealmRecord> records;

    public SecretRealmWorldAttachment() {
        this(List.of());
    }

    private SecretRealmWorldAttachment(List<SecretRealmRecord> records) {
        this.records = new ArrayList<>(records);
    }

    public List<SecretRealmRecord> records() {
        return this.records;
    }

    public void replaceAll(List<SecretRealmRecord> value) {
        this.records.clear();
        this.records.addAll(value);
    }
}
