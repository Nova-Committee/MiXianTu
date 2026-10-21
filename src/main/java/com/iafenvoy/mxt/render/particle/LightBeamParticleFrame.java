package com.iafenvoy.mxt.render.particle;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public final class LightBeamParticleFrame {
    private final List<Request> requests = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> view = Collections.unmodifiableList(this.entries);

    public void clear() {
        this.requests.clear();
        this.entries.clear();
    }

    public void add(LightBeamParticleShape shape, float age, float phase, Vec3 camera) {
        this.requests.add(new Request(shape, age, phase, shape.bounds().distanceToSqr(camera)));
    }

    public void build(Vec3 camera, Predicate<AABB> visible) {
        this.entries.clear();
        this.requests.sort(Comparator.comparingDouble(Request::distanceSquared));
        int count = Math.min(this.requests.size(), LightBeamParticleMesh.MAX_INSTANCES);
        double weight = 0;
        for (int i = 0; i < count; i++) weight += this.requests.get(i).weight();
        int remaining = LightBeamParticleMesh.MAX_INSTANCES - count;
        for (int i = 0; i < count; i++) {
            Request request = this.requests.get(i);
            int quota = Math.min(LightBeamParticleShape.MAX_SEGMENTS, 1 + (int) Math.floor(remaining * request.weight() / weight));
            request.shape.segments(request.age, request.phase, quota, segment -> {
                if (visible.test(request.shape.segmentBounds(segment)))
                    this.entries.add(new Entry(request.shape, segment, request.shape.sortCenter(segment).distanceToSqr(camera)));
            });
        }
        this.entries.sort(Comparator.comparingDouble(Entry::distanceSquared).reversed());
    }

    public List<Entry> entries() {
        return this.view;
    }

    private record Request(LightBeamParticleShape shape, float age, float phase, double distanceSquared) {
        double weight() {
            return 1.0D / (1.0D + Math.sqrt(this.distanceSquared) / 32.0D);
        }
    }

    public record Entry(LightBeamParticleShape shape, LightBeamParticleShape.Segment segment, double distanceSquared) {
    }
}
