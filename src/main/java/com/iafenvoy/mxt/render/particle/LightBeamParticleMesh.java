package com.iafenvoy.mxt.render.particle;

import java.util.function.IntConsumer;

public final class LightBeamParticleMesh {
    public static final int BATCH_SIZE = 64;
    public static final int INSTANCE_BYTES = 96;
    public static final int MAX_INSTANCES = 8192;
    public static final int LOD_COUNT = 3;

    private LightBeamParticleMesh() {
    }

    public static int lod(double distanceSquared) {
        return distanceSquared < 32 * 32 ? 0 : distanceSquared < 96 * 96 ? 1 : 2;
    }

    public static int sides(int lod) {
        return switch (lod) {
            case 0 -> 8;
            case 1 -> 6;
            case 2 -> 4;
            default -> throw new IllegalArgumentException("Unknown light beam LOD: " + lod);
        };
    }

    public static int sections(int lod) {
        return switch (lod) {
            case 0 -> 16;
            case 1 -> 8;
            case 2 -> 5;
            default -> throw new IllegalArgumentException("Unknown light beam LOD: " + lod);
        };
    }

    public static int vertexCount(int lod) {
        return 2 * ((sections(lod) + 1) * (sides(lod) + 1) + 2 * (sides(lod) + 2));
    }

    public static int indexCount(int lod) {
        return sides(lod) * (sections(lod) * 12 + 12);
    }

    public static void write(int lod, VertexSink vertices, IntConsumer indices) {
        int sides = sides(lod), sections = sections(lod), offset = 0;
        for (int layer = 0; layer < 2; layer++) {
            for (int ring = 0; ring <= sections; ring++)
                for (int side = 0; side <= sides; side++)
                    vertices.add((float) Math.cos(side * Math.TAU / sides), (float) Math.sin(side * Math.TAU / sides),
                            ring, layer + lod * 4, (float) side / sides);
            for (int ring = 0; ring < sections; ring++)
                for (int side = 0; side < sides; side++) {
                    int a = offset + ring * (sides + 1) + side, b = a + sides + 1;
                    triangle(indices, a, b, b + 1);
                    triangle(indices, a, b + 1, a + 1);
                }
            offset += (sections + 1) * (sides + 1);
        }
        for (int layer = 0; layer < 2; layer++)
            for (int cap = 0; cap < 2; cap++) {
                vertices.add(0, 0, cap * sections, layer + 2 + lod * 4, 0);
                for (int side = 0; side <= sides; side++)
                    vertices.add((float) Math.cos(side * Math.TAU / sides), (float) Math.sin(side * Math.TAU / sides),
                            cap * sections, layer + 2 + lod * 4, (float) side / sides);
                for (int side = 0; side < sides; side++)
                    triangle(indices, offset, offset + side + (cap == 0 ? 1 : 2), offset + side + (cap == 0 ? 2 : 1));
                offset += sides + 2;
            }
    }

    private static void triangle(IntConsumer indices, int a, int b, int c) {
        indices.accept(a);
        indices.accept(b);
        indices.accept(c);
    }

    @FunctionalInterface
    public interface VertexSink {
        void add(float x, float y, float ring, float mode, float u);
    }
}
