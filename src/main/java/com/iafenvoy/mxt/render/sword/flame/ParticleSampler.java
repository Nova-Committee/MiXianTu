package com.iafenvoy.mxt.render.sword.flame;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public final class ParticleSampler implements AutoCloseable {
    public static final int MAX_PARTICLES = 256;
    public static final int SURFACE_RINGS = 24;
    private final GpuBuffer particles;
    private final GpuBuffer surface;
    private final GpuBuffer tile;
    private final GpuBuffer indices;

    public ParticleSampler() {
        this.particles = vertices(MAX_PARTICLES, false);
        this.surface = vertices((SURFACE_RINGS - 1) * 8, true);
        ByteBuffer data = MemoryUtil.memAlloc(4 * 12);
        try {
            vertex(data, 0, 0, 0);
            vertex(data, 1, 0, 0);
            vertex(data, 1, 1, 0);
            vertex(data, 0, 1, 0);
            data.flip();
            this.tile = RenderSystem.getDevice().createBuffer(() -> "Sword fluid tile", GpuBuffer.USAGE_VERTEX, data);
        } finally {
            MemoryUtil.memFree(data);
        }
        data = MemoryUtil.memAlloc(MAX_PARTICLES * 6 * 2);
        try {
            for (int quad = 0; quad < MAX_PARTICLES; quad++) {
                int offset = quad * 4;
                for (int index : new int[]{0, 1, 2, 2, 3, 0}) data.putShort((short) (offset + index));
            }
            data.flip();
            this.indices = RenderSystem.getDevice().createBuffer(() -> "Sword flame indices", GpuBuffer.USAGE_INDEX, data);
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    private static GpuBuffer vertices(int quads, boolean isSurface) {
        ByteBuffer data = MemoryUtil.memAlloc(quads * 4 * 12);
        try {
            for (int quad = 0; quad < quads; quad++) {
                if (isSurface) {
                    float u0 = (quad % 8) / 8.0F;
                    float u1 = u0 + 0.125F;
                    float ring = quad / 8;
                    vertex(data, u0, ring, 0);
                    vertex(data, u1, ring, 0);
                    vertex(data, u1, ring + 1, 0);
                    vertex(data, u0, ring + 1, 0);
                } else {
                    vertex(data, -1, -1, quad);
                    vertex(data, 1, -1, quad);
                    vertex(data, 1, 1, quad);
                    vertex(data, -1, 1, quad);
                }
            }
            data.flip();
            return RenderSystem.getDevice().createBuffer(() -> "Sword flame template", GpuBuffer.USAGE_VERTEX, data);
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    private static void vertex(ByteBuffer data, float x, float y, float z) {
        data.putFloat(x).putFloat(y).putFloat(z);
    }

    public GpuBuffer particles() {
        return this.particles;
    }

    public GpuBuffer surface() {
        return this.surface;
    }

    public GpuBuffer tile() {
        return this.tile;
    }

    public GpuBuffer indices() {
        return this.indices;
    }

    public VertexFormat.IndexType indexType() {
        return VertexFormat.IndexType.SHORT;
    }

    @Override
    public void close() {
        this.particles.close();
        this.surface.close();
        this.tile.close();
        this.indices.close();
    }
}
