package com.iafenvoy.mxt.attachment;

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
 * Chunk-local plant rows, packed position to the age the plant has reached and the game time that age was measured
 * at.
 * <p>
 * Both numbers are needed. The age cannot be re-derived from the clock alone: growth depends on the age a period
 * starts from, so replaying a capped window of time gives a different answer depending on where the window starts.
 * Storing the age makes a read idempotent - reading a plant twice returns the same number instead of charging it
 * again - and the clock is what the next settlement measures the elapsed span from.
 * <p>
 * A negative clock is a naturally generated plant nobody has touched: the rolled age is parked in the age field and
 * the clock holds no meaningful value, so the first read can turn it into a real row. A non-negative clock is a
 * plant placed in the world, written the moment it was put down, and its age is settled from that moment on.
 * <p>
 * Stored as a list of rows rather than a map keyed by the packed position: a packed-position key made saving a
 * populated index fail outright in {@code FormationWorldAttachment}.
 */
public final class HerbChunkAttachment {
    public static final MapCodec<HerbChunkAttachment> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            // Tolerant per row: one unreadable row is dropped with a named warning instead of costing the chunk.
            CollectionCodecs.list(Stored.CODEC).lenientOptionalFieldOf("plants", List.of()).forGetter(HerbChunkAttachment::stored)
    ).apply(i, HerbChunkAttachment::new));
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<Long, Plant> plants;

    public HerbChunkAttachment() {
        this(List.of());
    }

    private HerbChunkAttachment(List<Stored> stored) {
        this.plants = new LinkedHashMap<>(stored.size());
        for (Stored entry : stored) {
            // A repeated position is a hand-edited save; losing that row is better than losing the chunk.
            if (this.plants.putIfAbsent(entry.position(), new Plant(entry.age(), entry.clock())) != null)
                LOGGER.warn("Ignoring duplicate herb plant in the saved chunk: {}", BlockPos.of(entry.position()));
        }
    }

    public Optional<Plant> find(BlockPos position) {
        return Optional.ofNullable(this.plants.get(position.asLong()));
    }

    public void set(BlockPos position, double age, long clock) {
        this.plants.put(position.asLong(), new Plant(age, clock));
    }

    public boolean remove(BlockPos position) {
        return this.plants.remove(position.asLong()) != null;
    }

    public boolean isEmpty() {
        return this.plants.isEmpty();
    }

    public Map<BlockPos, Plant> plants() {
        Map<BlockPos, Plant> result = new LinkedHashMap<>();
        this.plants.forEach((position, plant) -> result.put(BlockPos.of(position), plant));
        return result;
    }

    private List<Stored> stored() {
        return this.plants.entrySet().stream()
                .map(entry -> new Stored(entry.getKey(), entry.getValue().age(), entry.getValue().clock()))
                .toList();
    }

    /**
     * The age a plant has reached and the game time it was measured at. A negative clock means the row is still an
     * unsettled wild roll, and then {@code age} is meaningless.
     */
    public record Plant(double age, long clock) {
    }

    private record Stored(long position, double age, long clock) {
        private static final Codec<Stored> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("position").forGetter(Stored::position),
                Codec.DOUBLE.fieldOf("age").forGetter(Stored::age),
                Codec.LONG.fieldOf("clock").forGetter(Stored::clock)
        ).apply(i, Stored::new));
    }
}
