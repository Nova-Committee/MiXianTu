package com.iafenvoy.mxt.runtime.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;

/**
 * Persisted custom aura areas. Formation areas are derived live and are deliberately not stored here.
 */
public final class AuraWorldAttachment {
    public static final MapCodec<AuraWorldAttachment> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Area.CODEC).optionalFieldOf("areas", Map.of()).forGetter(AuraWorldAttachment::encoded)
    ).apply(i, AuraWorldAttachment::new));
    public static final Codec<AuraWorldAttachment> CODEC = MAP_CODEC.codec();
    private final Map<String, Area> areas;

    public AuraWorldAttachment() {
        this(Map.of());
    }

    private AuraWorldAttachment(Map<String, Area> areas) {
        this.areas = new LinkedHashMap<>(areas);
    }

    private Map<String, Area> encoded() {
        return this.areas;
    }

    public Map<String, Area> areas() {
        return this.areas;
    }

    public String add(Area area) {
        String id = UUID.randomUUID().toString();
        this.areas.put(id, area);
        return id;
    }

    public boolean remove(String id) {
        return this.areas.remove(id) != null;
    }

    public Optional<Entry<String, Area>> bestAt(BlockPos pos) {
        return this.areas.entrySet().stream().filter(entry -> entry.getValue().contains(pos)).max(Entry.comparingByValue());
    }

    public record Area(Identifier zone, Shape shape, int priority) implements Comparable<Area> {
        public static final Codec<Area> CODEC = RecordCodecBuilder.create(i -> i.group(Identifier.CODEC.fieldOf("zone").forGetter(Area::zone), Shape.CODEC.fieldOf("shape").forGetter(Area::shape), Codec.INT.optionalFieldOf("priority", 0).forGetter(Area::priority)).apply(i, Area::new));

        boolean contains(BlockPos pos) {
            return this.shape.contains(pos);
        }

        @Override
        public int compareTo(Area other) {
            return Integer.compare(this.priority, other.priority);
        }
    }

    public record Shape(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public static final Codec<Shape> CODEC = RecordCodecBuilder.create(i -> i.group(Codec.INT.fieldOf("min_x").forGetter(Shape::minX), Codec.INT.fieldOf("min_y").forGetter(Shape::minY), Codec.INT.fieldOf("min_z").forGetter(Shape::minZ), Codec.INT.fieldOf("max_x").forGetter(Shape::maxX), Codec.INT.fieldOf("max_y").forGetter(Shape::maxY), Codec.INT.fieldOf("max_z").forGetter(Shape::maxZ)).apply(i, Shape::new));

        boolean contains(BlockPos pos) {
            return pos.getX() >= this.minX && pos.getX() <= this.maxX && pos.getY() >= this.minY && pos.getY() <= this.maxY && pos.getZ() >= this.minZ && pos.getZ() <= this.maxZ;
        }
    }
}
