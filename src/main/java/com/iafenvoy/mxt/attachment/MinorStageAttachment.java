package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.util.AbstractChangeTrackable;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The highest minor stage a body has reached inside each realm stage. Only the record grows, so an unlock survives
 * a breakthrough even though the progress that reached it restarts.
 */
public final class MinorStageAttachment extends AbstractChangeTrackable {
    public static final MapCodec<MinorStageAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            CollectionCodecs.map(RealmStage.CODEC, Codec.INT).lenientOptionalFieldOf("minor_stage_records", Map.of()).forGetter(MinorStageAttachment::minorStageRecords)
    ).apply(i, MinorStageAttachment::new));
    private final Map<Holder<RealmStage>, Integer> minorStageRecords;

    public MinorStageAttachment() {
        this(Map.of());
    }

    private MinorStageAttachment(Map<Holder<RealmStage>, Integer> minorStageRecords) {
        this.minorStageRecords = new LinkedHashMap<>(minorStageRecords);
    }

    // Highest minor stage reached inside each realm stage, 0-based. A key means "this body has stood in that
    // realm at all", and a value only ever grows, so a breakthrough never takes an unlock back with it.
    public Map<Holder<RealmStage>, Integer> minorStageRecords() {
        return this.minorStageRecords;
    }

    // -1 for a realm the body never entered, which is below every legal index.
    public int minorStageRecord(Holder<RealmStage> stage) {
        return this.minorStageRecords.getOrDefault(stage, -1);
    }

    public boolean raiseMinorStageRecord(Holder<RealmStage> stage, int index) {
        if (stage == null || index < 0) return false;
        Integer recorded = this.minorStageRecords.get(stage);
        if (recorded != null && recorded >= index) return false;
        this.minorStageRecords.put(stage, index);
        this.markDirty();
        return true;
    }

    // Reincarnation: the records describe how far one life got inside a realm, so the next life starts over.
    public void clearMinorStageRecords() {
        if (this.minorStageRecords.isEmpty()) return;
        this.minorStageRecords.clear();
        this.markDirty();
    }
}
