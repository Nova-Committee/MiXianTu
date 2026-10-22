package com.iafenvoy.mxt.runtime.world;

import net.minecraft.core.SectionPos;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

public final class AuraVisitorIndex {
    private final int minY;
    private final int maxY;
    private final Map<UUID, SectionPos> players = new HashMap<>();

    public AuraVisitorIndex(int minY, int maxY) {
        if (minY >= maxY) throw new IllegalArgumentException("Section height range cannot be empty");
        this.minY = minY;
        this.maxY = maxY;
    }

    public int update(UUID player, SectionPos center, ObjIntConsumer<SectionPos> changes) {
        SectionPos previous = this.players.put(player, center);
        if (center.equals(previous)) return 0;
        return (previous == null ? 0 : this.change(previous, center, -1, changes)) + this.change(center, previous, 1, changes);
    }

    public int retain(Set<UUID> present, ObjIntConsumer<SectionPos> changes) {
        int changed = 0;
        Iterator<Map.Entry<UUID, SectionPos>> iterator = this.players.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, SectionPos> entry = iterator.next();
            if (present.contains(entry.getKey())) continue;
            changed += this.change(entry.getValue(), null, -1, changes);
            iterator.remove();
        }
        return changed;
    }

    private int change(SectionPos center, SectionPos excluded, int delta, ObjIntConsumer<SectionPos> changes) {
        int changed = 0;
        for (int x = center.x() - 3; x <= center.x() + 3; x++) {
            for (int z = center.z() - 3; z <= center.z() + 3; z++) {
                for (int y = Math.max(this.minY, center.y() - 3); y <= Math.min(this.maxY - 1, center.y() + 3); y++) {
                    if (excluded != null && Math.abs(x - excluded.x()) <= 3
                            && Math.abs(y - excluded.y()) <= 3 && Math.abs(z - excluded.z()) <= 3) continue;
                    changes.accept(SectionPos.of(x, y, z), delta);
                    changed++;
                }
            }
        }
        return changed;
    }

    public void forEachCenter(Consumer<SectionPos> consumer) {
        this.players.values().forEach(consumer);
    }

    public void clear() {
        this.players.clear();
    }
}
