package com.iafenvoy.mxt.runtime.rift;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The meshes a rift is drawn from. A rift is a node in a graph rather than a surface: every block draws one cube
 * at its own centre, a beam towards each rift next to it, and its own share of every closed three-block loop.
 * Everything is built in block-local coordinates, so a caller only has to place and colour it.
 * <p>
 * No mesh is ever built for the whole structure, which keeps the cost per block constant and means adding or
 * removing a block never invalidates what another block has drawn. Every vertex list is a whole number of
 * four-vertex faces, which is what the position-colour quad pipeline expects.
 */
public final class RiftMesh {
    // Side of the point cube, diameter of a link and thickness of a triangle: the client config's starting value.
    public static final double DEFAULT_THICKNESS = 0.125;
    // The lower bound keeps a rift visible at all, the upper one stops a block being drawn larger than itself.
    public static final double MIN_THICKNESS = 0.0001;
    public static final double MAX_THICKNESS = 0.5;
    // The centre of a rift's own block, and the point every mesh is built around.
    public static final Vec3 CENTRE = new Vec3(0.5, 0.5, 0.5);
    private static final double EPSILON = 1.0E-9;

    private RiftMesh() {
    }

    // Six quads facing outwards, so the point keeps its volume from every angle instead of flattening to a square.
    public static List<Vec3> node(double thickness) {
        double half = thickness / 2.0;
        double low = 0.5 - half;
        double high = 0.5 + half;
        Vec3 a = new Vec3(low, low, low);
        Vec3 b = new Vec3(high, low, low);
        Vec3 c = new Vec3(high, high, low);
        Vec3 d = new Vec3(low, high, low);
        Vec3 e = new Vec3(low, low, high);
        Vec3 f = new Vec3(high, low, high);
        Vec3 g = new Vec3(high, high, high);
        Vec3 h = new Vec3(low, high, high);
        List<Vec3> quads = new ArrayList<>(24);
        quad(quads, a, e, h, d);
        quad(quads, b, c, g, f);
        quad(quads, a, b, f, e);
        quad(quads, d, h, g, c);
        quad(quads, a, d, c, b);
        quad(quads, e, f, g, h);
        return List.copyOf(quads);
    }

    // Two linked rifts draw two halves that meet exactly, so neither has to know whether the other is drawn.
    public static List<Vec3> linkShare(Vec3 neighbour, double thickness) {
        return beam(CENTRE, CENTRE.add(neighbour).scale(0.5), thickness / 2.0);
    }

    // The corner from own to the centroid, bounded by the midpoints towards the two partners. The partners must be
    // given in a cycle all three agree on: the caller passes its own two neighbours in the order the three sort.
    public static List<Vec3> triangleFace(Vec3 own, Vec3 forward, Vec3 backward) {
        Vec3 centroid = own.add(forward).add(backward).scale(1.0 / 3.0);
        return List.of(own, own.add(forward).scale(0.5), centroid, own.add(backward).scale(0.5));
    }

    // Both faces are drawn because the fill must be as thick as the lines bounding it; the rims only run along the
    // triangle's own two edges, since the other two are medians where the neighbouring shares meet face to face.
    public static List<Vec3> triangleShare(Vec3 own, Vec3 forward, Vec3 backward, double thickness) {
        Vec3 normal = forward.subtract(own).cross(backward.subtract(own)).normalize();
        if (normal.lengthSqr() < EPSILON) return List.of();
        Vec3 offset = normal.scale(thickness / 2.0);
        List<Vec3> face = triangleFace(own, forward, backward);
        Vec3 top0 = face.get(0).add(offset);
        Vec3 top1 = face.get(1).add(offset);
        Vec3 top2 = face.get(2).add(offset);
        Vec3 top3 = face.get(3).add(offset);
        Vec3 bottom0 = face.get(0).subtract(offset);
        Vec3 bottom1 = face.get(1).subtract(offset);
        Vec3 bottom2 = face.get(2).subtract(offset);
        Vec3 bottom3 = face.get(3).subtract(offset);
        List<Vec3> quads = new ArrayList<>(16);
        quad(quads, top0, top1, top2, top3);
        quad(quads, bottom0, bottom1, bottom2, bottom3);
        quad(quads, top0, top1, bottom1, bottom0);
        quad(quads, top3, top0, bottom0, bottom3);
        return List.copyOf(quads);
    }

    // Diagnostics and tests only: the drawing submits the vertices and never asks how large they are.
    public static double area(List<Vec3> polygon) {
        if (polygon.size() < 3) return 0.0;
        Vec3 origin = polygon.getFirst();
        double sum = 0.0;
        for (int index = 1; index + 1 < polygon.size(); index++)
            sum += origin.vectorTo(polygon.get(index)).cross(origin.vectorTo(polygon.get(index + 1))).length() / 2.0;
        return sum;
    }

    // The ends are left open: one sits inside the point cube and the other meets the same cross-section again.
    private static List<Vec3> beam(Vec3 from, Vec3 to, double radius) {
        Vec3 axis = to.subtract(from);
        if (axis.lengthSqr() < EPSILON) return List.of();
        Vec3 unit = axis.normalize();
        // Any vector that is not parallel to the axis gives a frame; the branch keeps them far enough apart.
        Vec3 helper = Math.abs(unit.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        Vec3 side = unit.cross(helper).normalize();
        Vec3 up = unit.cross(side).normalize();
        Vec3 opposite = side.scale(-1.0);
        Vec3[] ring = {
                side.add(up).scale(radius),
                side.subtract(up).scale(radius),
                opposite.subtract(up).scale(radius),
                opposite.add(up).scale(radius)};
        List<Vec3> quads = new ArrayList<>(16);
        for (int index = 0; index < ring.length; index++) {
            Vec3 start = ring[index];
            Vec3 end = ring[(index + 1) % ring.length];
            quad(quads, from.add(start), from.add(end), to.add(end), to.add(start));
        }
        return List.copyOf(quads);
    }

    private static void quad(List<Vec3> out, Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
        out.add(a);
        out.add(b);
        out.add(c);
        out.add(d);
    }
}
