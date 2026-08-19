package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.runtime.formation.FormationInstance.Snapshot;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Persistable level-scoped index of active formations, keyed by their validated controller position.
 */
public final class FormationWorldData {
    public static final MapCodec<FormationWorldData> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.LONG, Snapshot.CODEC).optionalFieldOf("formations", Map.of()).forGetter(FormationWorldData::encoded)
    ).apply(i, FormationWorldData::new));
    public static final Codec<FormationWorldData> CODEC = MAP_CODEC.codec();
    private final Map<Long, Snapshot> formations;

    public FormationWorldData() {
        this(Map.of());
    }

    private FormationWorldData(Map<Long, Snapshot> formations) {
        this.formations = new LinkedHashMap<>(formations);
    }

    public Optional<Snapshot> get(BlockPos position) {
        return Optional.ofNullable(this.formations.get(position.asLong()));
    }

    public boolean put(BlockPos position, FormationInstance instance) {
        long key = position.asLong();
        if (this.formations.containsKey(key)) return false;
        this.formations.put(key, instance.snapshot());
        return true;
    }

    public void replace(BlockPos position, FormationInstance instance) {
        this.formations.put(position.asLong(), instance.snapshot());
    }

    public Optional<Snapshot> remove(BlockPos position) {
        return Optional.ofNullable(this.formations.remove(position.asLong()));
    }

    public Map<BlockPos, Snapshot> formations() {
        Map<BlockPos, Snapshot> result = new LinkedHashMap<>();
        this.formations.forEach((position, snapshot) -> result.put(BlockPos.of(position), snapshot));
        return result;
    }

    private Map<Long, Snapshot> encoded() {
        return this.formations;
    }
}
