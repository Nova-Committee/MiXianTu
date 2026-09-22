package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persistable level-scoped index of active formations, keyed by their validated controller position. It holds
 * live {@link FormationInstance} objects, so a caller that reaches one through this attachment mutates the stored
 * state directly; {@link #formations()} copies the map but not its values. Stored as a list of rows rather than a
 * map, because keying by the packed position made saving a populated index fail outright.
 */
public final class FormationWorldAttachment {
    public static final MapCodec<FormationWorldAttachment> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            // Tolerant per row, via the shared list codec: one unreadable or invalid entry is dropped with a
            // named warning instead of costing every other formation in the level.
            CollectionCodecs.list(Stored.CODEC).optionalFieldOf("formations", List.of()).forGetter(FormationWorldAttachment::stored)
    ).apply(i, FormationWorldAttachment::new));
    public static final Codec<FormationWorldAttachment> CODEC = MAP_CODEC.codec();
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<Long, FormationInstance> formations;

    public FormationWorldAttachment() {
        this(List.of());
    }

    private FormationWorldAttachment(List<Stored> stored) {
        this.formations = new LinkedHashMap<>(stored.size());
        for (Stored entry : stored) {
            FormationInstance previous = this.formations.putIfAbsent(entry.position(), entry.formation());
            // A repeated controller is a hand-edited save; losing that row is better than losing the index.
            if (previous != null)
                LOGGER.warn("Ignoring duplicate formation controller in the saved index: {}", entry.position());
        }
    }

    public Optional<FormationInstance> get(BlockPos position) {
        return Optional.ofNullable(this.formations.get(position.asLong()));
    }

    public boolean put(BlockPos position, FormationInstance instance) {
        long key = position.asLong();
        if (this.formations.containsKey(key)) return false;
        this.formations.put(key, instance);
        return true;
    }

    public Optional<FormationInstance> remove(BlockPos position) {
        return Optional.ofNullable(this.formations.remove(position.asLong()));
    }

    public Map<BlockPos, FormationInstance> formations() {
        Map<BlockPos, FormationInstance> result = new LinkedHashMap<>();
        this.formations.forEach((position, instance) -> result.put(BlockPos.of(position), instance));
        return result;
    }

    private List<Stored> stored() {
        return this.formations.entrySet().stream().map(entry -> new Stored(entry.getKey(), entry.getValue())).toList();
    }

    // One row of the index. Named fields rather than Codec.pair's positional [position, instance], so a save
    // file says which number is which.
    private record Stored(long position, FormationInstance formation) {
        private static final Codec<Stored> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("position").forGetter(Stored::position),
                FormationInstance.CODEC.fieldOf("formation").forGetter(Stored::formation)
        ).apply(i, Stored::new));
    }
}
