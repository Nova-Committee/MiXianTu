package com.iafenvoy.mxt.runtime.rift;

import com.iafenvoy.mxt.data.rift.RiftPlacement;
import com.iafenvoy.mxt.data.rift.RiftShape;
import com.iafenvoy.mxt.data.rift.RiftVertex;
import com.mojang.serialization.DataResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RiftGeometry {
    public static final double THICKNESS = 0.025;
    private static final double EPS = 1e-9;
    private static final Vec3[] BOX_AXES = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};

    private RiftGeometry() {
    }

    public static DataResult<List<Triangle>> triangulate(RiftShape shape) {
        List<RiftVertex> points = shape.vertices();
        int size = points.size();
        if (size < 3 || size > RiftShape.MAX_VERTICES) return invalid("Rift requires 3..128 vertices");
        double minU = Double.POSITIVE_INFINITY, maxU = -minU, minV = minU, maxV = -minU;
        for (int i = 0; i < size; i++) {
            RiftVertex p = points.get(i), next = points.get((i + 1) % size);
            if (!Double.isFinite(p.u()) || !Double.isFinite(p.v()) || Math.abs(p.u()) > 1024 || Math.abs(p.v()) > 1024)
                return invalid("Rift vertex is non-finite or too far from origin");
            if (distanceSqr(p, next) < EPS * EPS) return invalid("Rift has a zero-length edge");
            RiftVertex previous = points.get((i + size - 1) % size);
            if (Math.abs(cross(previous, p, next)) < EPS
                    && (p.u() - previous.u()) * (next.u() - p.u()) + (p.v() - previous.v()) * (next.v() - p.v()) <= 0)
                return invalid("Rift edges overlap");
            minU = Math.min(minU, p.u());
            maxU = Math.max(maxU, p.u());
            minV = Math.min(minV, p.v());
            maxV = Math.max(maxV, p.v());
            for (int j = i + 1; j < size; j++) {
                if (j == i + 1 || i == 0 && j == size - 1) continue;
                if (intersect(p, next, points.get(j), points.get((j + 1) % size)))
                    return invalid("Rift edges intersect");
            }
        }
        if (maxU - minU > RiftShape.MAX_SIZE || maxV - minV > RiftShape.MAX_SIZE)
            return invalid("Rift exceeds 32 blocks on a local axis");
        double area = area(points);
        if (Math.abs(area) < 1e-6) return invalid("Rift area is degenerate");
        List<Integer> remaining = new ArrayList<>();
        // Keep straight-edge control points editable without creating degenerate triangles.
        for (int i = 0; i < size; i++)
            if (Math.abs(cross(points.get((i + size - 1) % size), points.get(i), points.get((i + 1) % size))) >= EPS)
                remaining.add(i);
        if (area < 0) Collections.reverse(remaining);
        List<Triangle> triangles = new ArrayList<>(size - 2);
        while (remaining.size() > 3) {
            boolean clipped = false;
            for (int i = 0; i < remaining.size(); i++) {
                int a = remaining.get((i + remaining.size() - 1) % remaining.size());
                int b = remaining.get(i), c = remaining.get((i + 1) % remaining.size());
                if (cross(points.get(a), points.get(b), points.get(c)) <= EPS) continue;
                boolean blocked = false;
                for (int j : remaining)
                    if (j != a && j != b && j != c && inside(points.get(j), points.get(a), points.get(b), points.get(c))) {
                        blocked = true;
                        break;
                    }
                if (blocked) continue;
                triangles.add(new Triangle(a, b, c));
                remaining.remove(i);
                clipped = true;
                break;
            }
            if (!clipped) return invalid("Rift triangulation failed");
        }
        triangles.add(new Triangle(remaining.get(0), remaining.get(1), remaining.get(2)));
        return DataResult.success(List.copyOf(triangles));
    }

    public static Mesh mesh(RiftShape shape, RiftPlacement placement, List<Triangle> triangles) {
        List<Vec3> points = shape.vertices().stream().map(placement::world).toList();
        Vec3 first = points.getFirst();
        double minX = first.x, minY = first.y, minZ = first.z, maxX = minX, maxY = minY, maxZ = minZ;
        for (Vec3 point : points) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
            maxZ = Math.max(maxZ, point.z);
        }
        List<Face> faces = triangles.stream().map(t -> new Face(points.get(t.a), points.get(t.b), points.get(t.c), placement.normal())).toList();
        return new Mesh(points, triangles, faces, new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(THICKNESS));
    }

    public static double area(List<RiftVertex> points) {
        double sum = 0;
        for (int i = 0; i < points.size(); i++) {
            RiftVertex a = points.get(i), b = points.get((i + 1) % points.size());
            sum += a.u() * b.v() - b.u() * a.v();
        }
        return sum * 0.5;
    }

    public static boolean contains(RiftShape shape, RiftVertex point) {
        boolean inside = false;
        List<RiftVertex> p = shape.vertices();
        for (int i = 0, j = p.size() - 1; i < p.size(); j = i++) {
            RiftVertex a = p.get(i), b = p.get(j);
            if (onSegment(a, b, point)) return true;
            if ((a.v() > point.v()) != (b.v() > point.v())
                    && point.u() < (b.u() - a.u()) * (point.v() - a.v()) / (b.v() - a.v()) + a.u()) inside = !inside;
        }
        return inside;
    }

    private static boolean inside(RiftVertex p, RiftVertex a, RiftVertex b, RiftVertex c) {
        return cross(a, b, p) >= -EPS && cross(b, c, p) >= -EPS && cross(c, a, p) >= -EPS;
    }

    private static boolean intersect(RiftVertex a, RiftVertex b, RiftVertex c, RiftVertex d) {
        double abC = cross(a, b, c), abD = cross(a, b, d), cdA = cross(c, d, a), cdB = cross(c, d, b);
        return abC * abD < 0 && cdA * cdB < 0 || onSegment(a, b, c) || onSegment(a, b, d)
                || onSegment(c, d, a) || onSegment(c, d, b);
    }

    private static boolean onSegment(RiftVertex a, RiftVertex b, RiftVertex p) {
        return Math.abs(cross(a, b, p)) < EPS && p.u() >= Math.min(a.u(), b.u()) - EPS
                && p.u() <= Math.max(a.u(), b.u()) + EPS && p.v() >= Math.min(a.v(), b.v()) - EPS
                && p.v() <= Math.max(a.v(), b.v()) + EPS;
    }

    private static double cross(RiftVertex a, RiftVertex b, RiftVertex c) {
        return (b.u() - a.u()) * (c.v() - a.v()) - (b.v() - a.v()) * (c.u() - a.u());
    }

    private static double distanceSqr(RiftVertex a, RiftVertex b) {
        return Math.pow(a.u() - b.u(), 2) + Math.pow(a.v() - b.v(), 2);
    }

    private static <T> DataResult<T> invalid(String message) {
        return DataResult.error(() -> message);
    }

    public record Triangle(int a, int b, int c) {
    }

    public record Mesh(List<Vec3> points, List<Triangle> triangles, List<Face> faces, AABB bounds) {
        public double contact(AABB box, Vec3 movement) {
            double first = Double.POSITIVE_INFINITY;
            for (Face face : this.faces) first = Math.min(first, face.contact(box, movement));
            return first;
        }
    }

    // Cached separating axes support both static contact and continuous linear AABB movement.
    public static final class Face {
        private final List<Projection> projections;

        private Face(Vec3 a, Vec3 b, Vec3 c, Vec3 normal) {
            List<Vec3> axes = new ArrayList<>(List.of(BOX_AXES));
            axes.add(normal);
            for (Vec3 edge : List.of(b.subtract(a), c.subtract(b), a.subtract(c)))
                for (Vec3 axis : BOX_AXES)
                    if (edge.cross(axis).lengthSqr() > EPS * EPS) axes.add(edge.cross(axis).normalize());
            this.projections = axes.stream().map(axis -> new Projection(axis,
                    Math.min(axis.dot(a), Math.min(axis.dot(b), axis.dot(c))) - THICKNESS,
                    Math.max(axis.dot(a), Math.max(axis.dot(b), axis.dot(c))) + THICKNESS)).toList();
        }

        private double contact(AABB box, Vec3 movement) {
            Vec3 center = box.getCenter();
            double enter = 0, exit = 1;
            for (Projection p : this.projections) {
                Vec3 axis = p.axis;
                double radius = (Math.abs(axis.x) * box.getXsize() + Math.abs(axis.y) * box.getYsize()
                        + Math.abs(axis.z) * box.getZsize()) * 0.5;
                double position = axis.dot(center), velocity = axis.dot(movement);
                double low = p.min - radius - position, high = p.max + radius - position;
                if (Math.abs(velocity) < EPS) {
                    if (low > 0 || high < 0) return Double.POSITIVE_INFINITY;
                } else {
                    double t0 = low / velocity, t1 = high / velocity;
                    enter = Math.max(enter, Math.min(t0, t1));
                    exit = Math.min(exit, Math.max(t0, t1));
                    if (enter > exit) return Double.POSITIVE_INFINITY;
                }
            }
            return enter <= exit ? enter : Double.POSITIVE_INFINITY;
        }
    }

    private record Projection(Vec3 axis, double min, double max) {
    }
}
