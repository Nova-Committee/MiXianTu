package com.iafenvoy.mxt.render.particle;

import java.util.List;

// Splits only adjacent records, preserving the global translucent order across pipeline changes.
public final class LightBeamParticleDraws {
    private final int[] batches = new int[LightBeamParticleMesh.MAX_INSTANCES];
    private final int[] starts = new int[LightBeamParticleMesh.MAX_INSTANCES];
    private final int[] counts = new int[LightBeamParticleMesh.MAX_INSTANCES];
    private final boolean[] glows = new boolean[LightBeamParticleMesh.MAX_INSTANCES];
    private int size;

    public void build(List<LightBeamParticleFrame.Entry> entries, boolean depth) {
        if (entries.size() > LightBeamParticleMesh.MAX_INSTANCES) throw new IllegalArgumentException("Light beam instance limit exceeded");
        this.size = 0;
        for (int start = 0; start < entries.size();) {
            int batch = start / LightBeamParticleMesh.BATCH_SIZE;
            int limit = Math.min(entries.size(), (batch + 1) * LightBeamParticleMesh.BATCH_SIZE);
            boolean glow = depth && entries.get(start).segment().glow();
            int end = start + 1;
            while (end < limit && (!depth || entries.get(end).segment().glow() == glow)) end++;
            this.batches[this.size] = batch;
            this.starts[this.size] = start % LightBeamParticleMesh.BATCH_SIZE;
            this.counts[this.size] = end - start;
            this.glows[this.size++] = glow;
            start = end;
        }
    }

    public int size() {
        return this.size;
    }

    public int batch(int draw) {
        return this.batches[draw];
    }

    public int first(int draw) {
        return this.starts[draw];
    }

    public int count(int draw) {
        return this.counts[draw];
    }

    public boolean glow(int draw) {
        return this.glows[draw];
    }
}
